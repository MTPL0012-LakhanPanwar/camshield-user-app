package com.sierra.camblock

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.hardware.camera2.CameraManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.Button
import androidx.core.app.NotificationCompat
import com.sierra.camblock.utils.BlockState
import com.sierra.camblock.utils.PrefsManager

/**
 * Foreground service that owns the blocking overlay and watches for direct
 * camera-hardware reservations via [CameraManager.AvailabilityCallback].
 *
 * Foreground-app detection is no longer performed here — that responsibility
 * now belongs to [CameraBlockerAccessibilityService], which pushes updates
 * into [BlockState]. This service simply reacts to state changes and keeps
 * the overlay in sync.
 */
class CameraBlockerService : Service() {

    companion object {
        private const val TAG = "CameraBlocker"
        @Volatile
        var isServiceRunning = false
            private set

        /**
         * Weakly-held reference to the currently-running service instance.
         * Used exclusively by [CameraBlockerAccessibilityService] (and the
         * camera-callback block path) to invoke [blockNowSynchronously]
         * without going through the main-thread handler queue. This saves
         * one frame of latency (~16 ms) which, on high-end Samsung /
         * OneUI devices with fast camera-app launches, is the difference
         * between the user seeing the camera preview briefly and seeing
         * a black overlay immediately.
         */
        @Volatile
        private var instance: CameraBlockerService? = null

        /**
         * Paint the blocking overlay on top of whatever is foreground,
         * RIGHT NOW, from the caller's thread. Must be invoked on the main
         * thread — callers (the accessibility service and the camera
         * callback) are already on the main thread by construction.
         *
         * Returns `true` if the block was applied, `false` if the service
         * is not currently running (in which case the caller should start
         * it and rely on the normal listener path).
         */
        fun blockNowSynchronously(): Boolean {
            val svc = instance ?: return false
            return svc.applyBlockImmediately()
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var cameraManager: CameraManager
    private lateinit var prefsManager: PrefsManager
    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var isOverlayShowing = false

    /**
     * Runnable used to coalesce multiple rapid [BlockState] notifications
     * into a single overlay refresh. When the accessibility service fires
     * several state mutations in quick succession (e.g. foregroundPackage,
     * isCameraAppForeground, requireAcknowledgement in sequence) we want
     * one UI update, not three.
     */
    private val updateOverlayRunnable = Runnable { updateOverlayState() }

    /**
     * Listener registered against [BlockState]. Fires on every foreground or
     * camera-availability change, giving us instant overlay updates without
     * polling. We debounce by removing any pending post and scheduling a
     * fresh one — this collapses bursts of state changes into a single
     * updateOverlayState invocation and eliminates visible flicker.
     */
    private val stateListener: () -> Unit = {
        handler.removeCallbacks(updateOverlayRunnable)
        handler.post(updateOverlayRunnable)
    }

    private val cameraCallback = object : CameraManager.AvailabilityCallback() {
        override fun onCameraUnavailable(cameraId: String) {
            super.onCameraUnavailable(cameraId)
            BlockState.isCameraInUse = true
            Log.d(TAG, "Camera unavailable (in use) cameraId=$cameraId")

            // Only block when:
            //   (a) the user is currently locked inside a facility, AND
            //   (b) the app holding the camera is NOT us.
            //
            // (b) is critical because our own ScanActivity opens the
            // camera to read the exit QR code — if we were to kick the
            // user back to Home here, they could never scan out.
            if (!prefsManager.isLocked) return
            if (CamShield.isOwnAppInForeground) {
                Log.d(TAG, "Own app is foreground — not blocking our own camera use")
                return
            }

            // Dedupe: Android frequently fires onCameraUnavailable for both
            // the front and back camera IDs in rapid succession during a
            // single app launch. Only the FIRST call needs to perform HOME
            // — subsequent ones would just re-trigger an already-in-flight
            // transition and contribute to flicker. `requireAcknowledgement`
            // is itself idempotent; we gate HOME on the sticky flag having
            // transitioned from false → true.
            val wasAlreadyBlocking = BlockState.needsAcknowledgement

            // Paint the overlay RIGHT NOW, synchronously, before we ask
            // the system to animate the HOME transition. This is the
            // key optimisation for Samsung / OneUI devices where the
            // HOME gesture can take 200-1000 ms under memory pressure;
            // by drawing the opaque overlay on top of the camera's
            // SurfaceView in the same main-thread frame, the camera
            // preview is covered within ~1 vsync regardless of how
            // slow the activity transition is.
            applyBlockImmediately()

            if (!wasAlreadyBlocking) {
                CameraBlockerAccessibilityService.performHome()
            }
        }

        override fun onCameraAvailable(cameraId: String) {
            super.onCameraAvailable(cameraId)
            BlockState.isCameraInUse = false
        }
    }

    override fun onCreate() {
        super.onCreate()
        prefsManager = PrefsManager(this)
        cameraManager = getSystemService(CAMERA_SERVICE) as CameraManager
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        // Pre-inflate the blocking overlay *before* the first camera-app
        // event arrives. Inflation is the slowest step in the block hot
        // path (measured ~20-60 ms on a cold JIT). Doing it once at
        // service start means showOverlay() → addView is the only work
        // left on the critical path — typically 1-2 ms.
        ensureOverlayInflated()

        startForegroundService()

        try {
            cameraManager.registerAvailabilityCallback(cameraCallback, handler)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register camera callback", e)
        }

        // Subscribe to push-based state updates from the Accessibility
        // service + our own camera callback. Replaces the previous 200 ms
        // polling loop entirely.
        BlockState.onStateChanged = stateListener

        // Publish the instance so the accessibility service (and the
        // static `blockNowSynchronously` helper) can reach us without
        // going through the handler queue.
        instance = this
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        isServiceRunning = true
        // Re-evaluate in case the state changed before we registered. Use
        // the coalescing runnable so this doesn't race with a concurrently
        // scheduled update from the state listener.
        handler.removeCallbacks(updateOverlayRunnable)
        handler.post(updateOverlayRunnable)
        return START_STICKY
    }

    override fun onDestroy() {
        isServiceRunning = false
        // Clear our listener before anything else to avoid callbacks firing
        // on a half-destroyed service.
        if (BlockState.onStateChanged === stateListener) {
            BlockState.onStateChanged = null
        }
        // Drop the static instance reference so blockNowSynchronously()
        // callers after destruction fall through to the start-service
        // path rather than invoking methods on a stopped service.
        if (instance === this) instance = null
        handler.removeCallbacksAndMessages(null)
        hideOverlay()
        try {
            cameraManager.unregisterAvailabilityCallback(cameraCallback)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to unregister camera callback", e)
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private fun startForegroundService() {
        val channelId = "CameraBlockerChannel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Camera Blocker Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Camera Lock Active")
            .setContentText("Monitoring for camera usage...")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .build()

        // Android 14 (API 34) enforces that the foregroundServiceType passed to
        // startForeground() matches the one declared in the manifest AND that the
        // app holds the corresponding FOREGROUND_SERVICE_* permission. The service
        // only observes camera availability (no capture), so we use specialUse.
        // Guarding with try/catch prevents a process-wide crash if the platform
        // still rejects the start (e.g. OEM restrictions, background launch denied).
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    1,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(1, notification)
            }
        } catch (e: Exception) {
            Log.e("CameraBlocker", "startForeground failed", e)
            // Stop self so the system does not keep the service in a half-started
            // state that would trigger ForegroundServiceDidNotStartInTimeException.
            stopSelf()
        }
    }

    private fun updateOverlayState() {
        if (!prefsManager.isLocked) {
            // Defensive: if the device is no longer locked (user scanned the
            // exit QR, for example) any sticky block state from a previous
            // session is irrelevant and must not carry over.
            BlockState.reset()
            hideOverlay()
            return
        }

        // Never cover our own UI. The Accessibility service already filters
        // out events from our own package, so `BlockState.foregroundPackage`
        // cannot reliably be used to detect our own foreground state; we use
        // the [CamShield.isOwnAppInForeground] counter instead. This also
        // ensures that if a stale sticky-acknowledgement flag somehow
        // survives, it will not obscure e.g. the exit-scan screen.
        if (CamShield.isOwnAppInForeground) {
            hideOverlay()
            return
        }

        if (BlockState.shouldBlock()) {
            showOverlay()
        } else {
            hideOverlay()
        }
    }

    /**
     * Inflate the overlay View and attach its click handlers. Idempotent —
     * calling it multiple times is a no-op after the first successful
     * inflate. Invoked from [onCreate] so the View is warm by the time
     * the first camera-app event arrives.
     */
    private fun ensureOverlayInflated() {
        if (overlayView != null) return
        try {
            val inflater = getSystemService(LAYOUT_INFLATER_SERVICE) as LayoutInflater
            val view = inflater.inflate(R.layout.activity_blocked, null)

            // "I Understand" dismissal:
            //   1. Clear the sticky flag so shouldBlock() returns false.
            //   2. Remove the overlay View immediately (do not rely on
            //      the state-change listener alone, which may run on
            //      the next frame).
            //   3. Bring the launcher back to front for good measure —
            //      the camera app may still be in the recents stack.
            view.findViewById<Button>(R.id.btnDismiss)?.setOnClickListener {
                Log.d(TAG, "User acknowledged block")
                BlockState.acknowledge()
                hideOverlay()
                try {
                    val startMain = Intent(Intent.ACTION_MAIN).apply {
                        addCategory(Intent.CATEGORY_HOME)
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                                Intent.FLAG_ACTIVITY_CLEAR_TOP
                    }
                    startActivity(startMain)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to launch home after dismiss", e)
                }
            }
            overlayView = view
        } catch (e: Exception) {
            Log.e(TAG, "Failed to pre-inflate overlay", e)
        }
    }

    /**
     * Synchronous block hot path. Sets the sticky acknowledgement flag
     * and paints the overlay immediately (same main-thread frame). Called
     * from [CameraBlockerAccessibilityService] and the camera-hardware
     * callback BEFORE [performGlobalAction] returns, so the opaque
     * overlay is on top of the camera SurfaceView within the same vsync.
     *
     * Returns `true` if the overlay is now showing (or was already
     * showing). The caller can then ask the accessibility service to
     * execute the HOME gesture to actually close the camera app.
     */
    internal fun applyBlockImmediately(): Boolean {
        if (!prefsManager.isLocked) return false
        if (CamShield.isOwnAppInForeground) return false
        BlockState.requireAcknowledgement()
        showOverlay()
        return isOverlayShowing
    }

    /**
     * Build the overlay's WindowManager.LayoutParams. Extracted so both
     * the normal [showOverlay] path and the hot-path [applyBlockImmediately]
     * share the exact same window configuration.
     */
    private fun buildOverlayLayoutParams(): WindowManager.LayoutParams {
        // Flags picked for maximum compatibility (Xiaomi, OneUI, Pixel):
        //   FLAG_NOT_TOUCH_MODAL  — keep window touchable within bounds
        //                            while allowing touches outside to
        //                            pass through (moot at fullscreen).
        //   FLAG_LAYOUT_IN_SCREEN — lay out under status/nav bars too.
        //   FLAG_KEEP_SCREEN_ON   — prevent dimming while overlay visible.
        //   FLAG_SHOW_WHEN_LOCKED — render even over secure lockscreen.
        //   FLAG_LAYOUT_NO_LIMITS — allow extending into cutout area.
        //   FLAG_FULLSCREEN       — request true fullscreen.
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_FULLSCREEN,
            PixelFormat.TRANSLUCENT
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            params.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        params.gravity = Gravity.CENTER
        return params
    }

    private fun showOverlay() {
        if (isOverlayShowing) return

        // Double check Overlay Permission
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            Log.e(TAG, "Cannot show overlay: Permission missing")
            return
        }

        // Defensive: if onCreate's pre-inflate failed for some reason
        // (OOM, theme resolution race), try inflating again on demand.
        ensureOverlayInflated()
        val view = overlayView ?: return

        try {
            view.systemUiVisibility = (View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)

            windowManager?.addView(view, buildOverlayLayoutParams())
            isOverlayShowing = true
            Log.d(TAG, "Overlay SHOWN")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show overlay", e)
        }
    }

    private fun hideOverlay() {
        if (!isOverlayShowing) return

        try {
            if (overlayView != null && windowManager != null) {
                windowManager?.removeView(overlayView)
            }
            isOverlayShowing = false
            Log.d("CameraBlocker", "Overlay HIDDEN")
        } catch (e: Exception) {
            Log.e("CameraBlocker", "Failed to hide overlay", e)
        }
    }

}