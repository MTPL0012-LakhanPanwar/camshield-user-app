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

        // 1. Set the sticky acknowledgement flag FIRST — before anything
        //    else races. Without this the next WINDOW_STATE_CHANGED event
        //    (fired when we jump to Home) would clear
        //    `isCameraAppForeground` and the overlay would be torn down
        //    immediately.
        BlockState.requireAcknowledgement()

        // 2. Make sure the overlay service is running BEFORE we switch to
        //    Home. If the service was not running, starting it now means
        //    its onCreate → updateOverlayState sees the sticky flag and
        //    renders the overlay on first paint after the HOME transition.
        ensureBlockerServiceRunning()

        // 3. Kick the user back to the launcher. This is the fastest
        //    possible "block" — dispatched synchronously by the system.
        //    The TYPE_APPLICATION_OVERLAY window from step 2 renders on
        //    top of the launcher so the user cannot interact with it
        //    until they acknowledge the block.
        try {
            performGlobalAction(GLOBAL_ACTION_HOME)
        } catch (e: Exception) {
            Log.e(TAG, "performGlobalAction(HOME) failed", e)
        }
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
