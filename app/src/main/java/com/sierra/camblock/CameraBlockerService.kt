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
            BlockState.requireAcknowledgement()
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

    private fun showOverlay() {
        if (isOverlayShowing) return

        // Double check Overlay Permission
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            Log.e("CameraBlocker", "Cannot show overlay: Permission missing")
            return
        }

        try {
            if (overlayView == null) {
                val inflater = getSystemService(LAYOUT_INFLATER_SERVICE) as LayoutInflater
                overlayView = inflater.inflate(R.layout.activity_blocked, null)

                // Set up dismiss button. The previous implementation only
                // fired a HOME intent which did not remove the overlay or
                // clear the sticky acknowledgement flag — so tapping the
                // button appeared to "do nothing" while the overlay stayed
                // visible. Now we:
                //   1. Clear the sticky flag so shouldBlock() returns false.
                //   2. Remove the overlay view immediately (do not rely on
                //      the state-change listener alone, which may run on
                //      the next frame).
                //   3. Bring the launcher back to front for good measure —
                //      the camera app may still be in the recents stack.
                val btnDismiss = overlayView?.findViewById<Button>(R.id.btnDismiss)
                btnDismiss?.setOnClickListener {
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
            }

            // Flags updated for better compatibility (Xiaomi, etc.)
            val layoutParams = WindowManager.LayoutParams(
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
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or // Allow extending outside screen
                        WindowManager.LayoutParams.FLAG_FULLSCREEN,         // Request full screen
                PixelFormat.TRANSLUCENT
            )

            // Handle Display Cutout (Notch)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutParams.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }

            layoutParams.gravity = Gravity.CENTER

            // Hide System UI (Immersive Mode)
            overlayView?.systemUiVisibility = (View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION // hide nav bar
                    or View.SYSTEM_UI_FLAG_FULLSCREEN // hide status bar
                    or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)

            windowManager?.addView(overlayView, layoutParams)
            isOverlayShowing = true
            Log.d("CameraBlocker", "Overlay SHOWN")
        } catch (e: Exception) {
            Log.e("CameraBlocker", "Failed to show overlay", e)
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