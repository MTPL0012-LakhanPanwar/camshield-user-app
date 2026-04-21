package com.sierra.camblock

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.sierra.camblock.utils.BlockState
import com.sierra.camblock.utils.CameraApps
import com.sierra.camblock.utils.OverlayController
import com.sierra.camblock.utils.PrefsManager
import java.lang.ref.WeakReference

/**
 * Detects foreground-app changes via the Android Accessibility framework
 * and triggers blocking *immediately* (no 200 ms polling loop as with the
 * previous UsageStatsManager implementation).
 *
 * Responsibilities:
 *   1. Listen for [AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED] events.
 *   2. Update [BlockState] with the new foreground package.
 *   3. When a camera app is detected while the device is locked, send the
 *      user back to Home via [performGlobalAction] — this is substantially
 *      faster than launching an overlay activity and does not require any
 *      additional permission.
 *   4. Make sure [CameraBlockerService] is running so the overlay window
 *      and camera-availability callback remain active.
 *
 * This service does not inspect window contents
 * (`canRetrieveWindowContent="false"` in the XML config) and therefore
 * surfaces a minimal consent screen to the user.
 */
class CameraBlockerAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "CamBlockA11y"

        /**
         * Weak reference to the current service instance. Held weakly so
         * that if the system recreates the service we never keep a stale
         * reference around. Only non-null while the service is connected.
         */
        @Volatile
        private var instanceRef: WeakReference<CameraBlockerAccessibilityService>? = null

        /**
         * Allows other components (notably [CameraBlockerService] when its
         * CameraManager callback fires) to push the user back to the home
         * screen without duplicating accessibility logic. Returns true when
         * the gesture was dispatched.
         */
        fun performHome(): Boolean {
            val svc = instanceRef?.get() ?: return false
            return try {
                svc.performGlobalAction(GLOBAL_ACTION_HOME)
            } catch (e: Exception) {
                Log.e(TAG, "performHome failed", e)
                false
            }
        }

        /** True when the service is currently bound by the system. */
        fun isRunning(): Boolean = instanceRef?.get() != null
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private lateinit var prefsManager: PrefsManager

    override fun onCreate() {
        super.onCreate()
        prefsManager = PrefsManager(this)
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instanceRef = WeakReference(this)

        // Programmatically reinforce the XML config. Some OEMs (notably
        // older Samsung / Xiaomi builds) ignore a few flags when supplied
        // only through XML, so we explicitly set them again here.
        //
        // IMPORTANT: TYPE_WINDOWS_CHANGED is *deliberately omitted* here —
        // subscribing to it caused an overlay flicker loop because every
        // addView / removeView of our TYPE_APPLICATION_OVERLAY produces a
        // TYPE_WINDOWS_CHANGED event with event.packageName set to our own
        // package, which used to be interpreted as "our app is in the
        // foreground" and force the overlay to hide — the removal of which
        // then fires yet another event, and so on.
        try {
            serviceInfo = (serviceInfo ?: AccessibilityServiceInfo()).apply {
                eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
                notificationTimeout = 0L
                flags = flags or
                        AccessibilityServiceInfo.DEFAULT or
                        AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
            }
            Log.d(TAG, "Accessibility service connected")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set serviceInfo", e)
        }

        // ------------------------------------------------------------------
        // "Clear all from recents" hardening (Samsung OneUI).
        //
        // After the user swipes all apps from recents the OS kills our
        // process, which wipes in-memory BlockState AND destroys the
        // foreground CameraBlockerService. The accessibility service is
        // restarted by the framework hundreds of ms BEFORE the foreground
        // service comes back up — creating a window in which the user
        // could open the camera with nothing to stop them.
        //
        // To close that window we:
        //   1. Pre-inflate the overlay here, so OverlayController.show()
        //      is ready the instant we need it.
        //   2. Wire up the SharedPrefs persistence sink for the sticky
        //      acknowledgement flag.
        //   3. Restore that flag from SharedPrefs and, if it was set at
        //      the time of the kill, PAINT THE OVERLAY RIGHT NOW — before
        //      the user has a chance to open anything. This guarantees
        //      that mid-block process death cannot bypass the blocker.
        //   4. Start the foreground service in the background so the
        //      camera-hardware callback comes back online.
        // ------------------------------------------------------------------
        OverlayController.initialize(this)
        BlockState.persistentAckSetter = { value ->
            prefsManager.needsAcknowledgement = value
        }
        if (prefsManager.isLocked && prefsManager.needsAcknowledgement) {
            Log.d(TAG, "Restoring sticky block after process restart")
            BlockState.restoreAcknowledgementFromPersistence(true)
            // Paint the overlay directly from the a11y service — don't
            // wait for CameraBlockerService to come back up.
            OverlayController.show(this)
        }

        // Make sure the overlay/foreground service is up. Starting it from
        // an already-running accessibility service respects the background
        // start restrictions introduced in Android 12+.
        ensureBlockerServiceRunning()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return

        val packageName = event.packageName?.toString().orEmpty()
        if (packageName.isEmpty()) return

        // Ignore system UI chrome — status bar, notification shade, IMEs —
        // otherwise toggling Wi-Fi would clear our "camera app foreground"
        // flag incorrectly. We treat those transient windows as "no change".
        if (isIgnorablePackage(packageName)) return

        // Ignore events from our OWN package. The overlay window, the
        // blocker service, and our activities should never be treated as
        // "foreground changed to a camera app". Critically, this prevents
        // the flicker loop where showing our overlay produced events that
        // then caused us to hide it.
        if (packageName == applicationContext.packageName) return

        handleForegroundChange(packageName)
    }

    override fun onInterrupt() {
        // No ongoing interactions to cancel.
    }

    override fun onDestroy() {
        instanceRef = null
        mainHandler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private fun handleForegroundChange(packageName: String) {
        BlockState.foregroundPackage = packageName

        val selfPackage = applicationContext.packageName
        val isCameraApp = CameraApps.isCameraApp(packageName, selfPackage)
        BlockState.isCameraAppForeground = isCameraApp

        if (!isCameraApp) return
        if (!prefsManager.isLocked) return

        Log.d(TAG, "Blocking camera app: $packageName")

        // FAST PATH (primary, same main-thread frame):
        //   Ask the already-running blocker service to paint its opaque
        //   overlay on top of the camera app RIGHT NOW. Because both
        //   services run on the main thread and the overlay View is
        //   pre-inflated in CameraBlockerService.onCreate(), this
        //   typically completes in 1-3 ms — well under a single vsync.
        //   On high-end Samsung S2x devices this is what prevents the
        //   camera preview from ever being visible, even when OneUI's
        //   HOME animation is slow.
        //
        //   `blockNowSynchronously` returns false only if the foreground
        //   service is not currently alive (e.g. the user just cleared
        //   recents and the system is still respawning it). In that
        //   case we fall back to painting the overlay DIRECTLY from the
        //   accessibility service via the shared [OverlayController] —
        //   which is already initialized with a pre-inflated View from
        //   our [onServiceConnected] — so the user never sees the
        //   preview, even during the foreground-service restart window.
        val blocked = CameraBlockerService.blockNowSynchronously()
        if (!blocked) {
            BlockState.requireAcknowledgement()
            OverlayController.show(this)
            ensureBlockerServiceRunning()
        }

        // Kick the user back to the launcher. `performGlobalAction`
        // is dispatched synchronously by the system; the actual HOME
        // animation may still take 200-1000 ms on OneUI under memory
        // pressure, but the overlay drawn above is already covering
        // the camera preview so there is nothing exploitable during
        // that transition window.
        try {
            performGlobalAction(GLOBAL_ACTION_HOME)
        } catch (e: Exception) {
            Log.e(TAG, "performGlobalAction(HOME) failed", e)
        }

        // Make sure the overlay/foreground service is up. Starting it from
        // an already-running accessibility service respects the background
        // start restrictions introduced in Android 12+.
        ensureBlockerServiceRunning()
    }

    private fun ensureBlockerServiceRunning() {
        if (!prefsManager.isLocked) return
        if (CameraBlockerService.isServiceRunning) return
        try {
            val intent = Intent(this, CameraBlockerService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Unable to start CameraBlockerService", e)
        }
    }

    private fun isIgnorablePackage(packageName: String): Boolean {
        return packageName.startsWith("com.android.systemui") ||
                packageName == "android" ||
                packageName.endsWith(".inputmethod") ||
                packageName.contains("inputmethod", ignoreCase = true)
    }
}
