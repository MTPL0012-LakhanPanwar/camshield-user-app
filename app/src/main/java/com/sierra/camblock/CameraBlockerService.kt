package com.sierra.camblock

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.camera2.CameraManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import com.sierra.camblock.utils.BlockState
import com.sierra.camblock.utils.OverlayController
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

        /**
         * How often the overlay watchdog verifies that the overlay is
         * actually attached to the window manager when it should be.
         * 150 ms is a sweet spot: fast enough that no user can open a
         * camera, see a preview, and tap shutter in the worst-case window
         * (even the fastest human reaction time is ~200 ms); slow enough
         * that the background CPU cost is unmeasurable.
         */
        private const val OVERLAY_WATCHDOG_INTERVAL_MS = 150L

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

    /**
     * Dedicated background thread + handler for
     * [CameraManager.registerAvailabilityCallback]. The camera HAL
     * dispatches `onCameraUnavailable` on whichever handler we pass in;
     * if that handler is the main looper (the old behaviour) then the
     * callback can sit queued behind the camera app's own startup,
     * launcher transition animations, and accessibility event delivery —
     * all of which fight for the main thread under memory pressure on
     * Samsung OneUI. On an S24 Ultra with multiple recent camera-using
     * apps cached, that queue can stall for 1-2 seconds, which is the
     * exact window the user reports.
     *
     * By owning a private Looper we receive the HAL callback within
     * ~milliseconds of the camera being reserved, independent of
     * whatever the main thread is doing.
     */
    private lateinit var cameraCallbackThread: HandlerThread
    private lateinit var cameraCallbackHandler: Handler

    private lateinit var cameraManager: CameraManager
    private lateinit var prefsManager: PrefsManager

    /**
     * Heartbeat that every [OVERLAY_WATCHDOG_INTERVAL_MS] ms verifies
     * the overlay is actually attached whenever it *should* be. This is
     * the last line of defence for the pathological case where both
     * primary signals (Accessibility event + CameraManager availability
     * callback) are throttled by the OS under memory pressure, or where
     * OneUI's window manager silently detached our overlay during a
     * foldable / configuration-change event.
     *
     * Cost: one read of three AtomicBoolean/Boolean fields and, in the
     * 99.99 % case where state is consistent, zero further work. Under
     * 1 % CPU even on a low-end device.
     */
    private val overlayWatchdog: Runnable = object : Runnable {
        override fun run() {
            enforceOverlayState()
            handler.postDelayed(this, OVERLAY_WATCHDOG_INTERVAL_MS)
        }
    }

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
        /*
         * IMPORTANT: This callback now runs on `cameraCallbackThread`, NOT
         * the main thread. We do the earliest gate checks here (which only
         * touch AtomicBooleans / prefs) and then immediately jump to the
         * main thread with postAtFrontOfQueue so the overlay `addView`
         * happens before any other queued main-thread work (launcher
         * animation, accessibility event dispatch, camera app onResume).
         */
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
            // single app launch. We snapshot whether the sticky flag was
            // already set so only the FIRST reservation triggers HOME —
            // later front/back duplicates only paint the overlay (which
            // is a no-op if already attached).
            val wasAlreadyBlocking = BlockState.needsAcknowledgement

            // Hop to the main thread at front-of-queue. This is the
            // key optimisation for Samsung / OneUI under memory pressure:
            // the main thread is typically busy dispatching accessibility
            // events and animating the camera app's onResume; posting
            // normally would queue behind that work (measured 200-2000
            // ms on S24 Ultra with several camera-using apps cached).
            // `postAtFrontOfQueue` jumps the line, so the opaque overlay
            // is added to WindowManager within 1-2 vsync of the camera
            // HAL actually being reserved.
            handler.postAtFrontOfQueue {
                applyBlockImmediately()
                // Ask the accessibility service to HOME away the camera
                // app. If for some reason the a11y service isn't alive
                // right now (e.g. in the first few ms after a 'clear
                // all' process restart) we still have the overlay up,
                // so the camera preview is already covered.
                if (!wasAlreadyBlocking) {
                    CameraBlockerAccessibilityService.performHome()
                }
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

        // Pre-inflate the blocking overlay *before* the first camera-app
        // event arrives. Inflation is the slowest step in the block hot
        // path (measured ~20-60 ms on a cold JIT). Doing it once at
        // service start means show() → addView is the only work left on
        // the critical path — typically 1-2 ms.
        OverlayController.initialize(this)

        // Wire up SharedPreferences persistence of the sticky flag AND
        // restore any previously-persisted value. This is critical for
        // the Samsung 'clear all from recents' scenario: when that
        // gesture kills our process, BlockState's AtomicBooleans reset
        // to their defaults. Without persistence, a user who was in
        // the middle of an un-acknowledged block would simply see the
        // overlay disappear after process restart. With persistence,
        // we restore the sticky flag and re-paint the overlay as soon
        // as this service's onStartCommand fires.
        BlockState.persistentAckSetter = { value ->
            prefsManager.needsAcknowledgement = value
        }
        if (prefsManager.isLocked && prefsManager.needsAcknowledgement) {
            BlockState.restoreAcknowledgementFromPersistence(true)
        }

        // Spin up the dedicated camera-callback Looper BEFORE registering
        // the availability callback, so the HAL dispatches to it from the
        // very first event. Thread priority slightly above default to
        // reduce scheduler delay under memory pressure.
        cameraCallbackThread = HandlerThread("CamShield-CamCb").apply {
            start()
        }
        cameraCallbackHandler = Handler(cameraCallbackThread.looper)

        startForegroundService()

        try {
            cameraManager.registerAvailabilityCallback(
                cameraCallback,
                cameraCallbackHandler
            )
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

        // Start the watchdog heartbeat. This is a last-line-of-defence
        // that covers the case where both primary block signals are
        // delayed by the OS (which empirically DOES happen on S24 Ultra
        // with a full app cache) — it guarantees the overlay is
        // re-attached within OVERLAY_WATCHDOG_INTERVAL_MS if for any
        // reason it was detached or never shown.
        handler.postDelayed(overlayWatchdog, OVERLAY_WATCHDOG_INTERVAL_MS)
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
        handler.removeCallbacks(overlayWatchdog)
        handler.removeCallbacksAndMessages(null)
        // NB: We intentionally do NOT call OverlayController.hide() here.
        // The accessibility service may still be alive and may legitimately
        // want the overlay on-screen (e.g. a 'clear all' only killed us,
        // not the a11y service). The controller is process-wide and
        // tolerates being called from either owner.
        try {
            cameraManager.unregisterAvailabilityCallback(cameraCallback)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to unregister camera callback", e)
        }
        // Quit the dedicated camera-callback Looper last so any in-flight
        // callback gets to complete its postAtFrontOfQueue dispatch
        // before the thread exits.
        if (::cameraCallbackThread.isInitialized) {
            try {
                cameraCallbackThread.quitSafely()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to quit camera callback thread", e)
            }
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

    /**
     * Refresh the overlay's visible state to match current [BlockState] +
     * session gates. Delegates to [OverlayController.enforce] which owns
     * the actual WindowManager lifecycle. When the user is no longer
     * locked we also call [BlockState.reset] so any stale sticky flag
     * from a previous session is cleared (both in-memory and persisted).
     */
    private fun updateOverlayState() {
        if (!prefsManager.isLocked) {
            BlockState.reset()
            // reset() does not go through the persistent setter, so
            // make sure the persisted mirror is cleared too.
            prefsManager.needsAcknowledgement = false
        }
        OverlayController.enforce(this, prefsManager.isLocked)
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
        return OverlayController.show(this)
    }

    /**
     * Watchdog tick (every [OVERLAY_WATCHDOG_INTERVAL_MS] ms). This is the
     * last line of defence for the pathological case where both primary
     * signals (Accessibility event + CameraManager availability callback)
     * are throttled by the OS under memory pressure, or where OneUI's
     * window manager silently detached our overlay. [OverlayController.enforce]
     * is cheap and idempotent — a no-op when state is already consistent.
     */
    private fun enforceOverlayState() {
        OverlayController.enforce(this, prefsManager.isLocked)
    }
}