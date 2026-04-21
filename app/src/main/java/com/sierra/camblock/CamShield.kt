package com.sierra.camblock

import android.app.Activity
import android.app.Application
import android.os.Bundle

/**
 * CamShield Application class.
 *
 * Owns a single process-wide signal — [isOwnAppInForeground] — that tells
 * blocker components whether one of our own activities is currently
 * visible to the user. This is consulted from:
 *
 *   * [CameraBlockerService.cameraCallback] — so that when
 *     `ScanActivity` opens the camera for QR scanning we do NOT treat our
 *     own legitimate camera use as a block-worthy event and kick the user
 *     back to Home (which would make the exit-scan flow impossible).
 *
 *   * [CameraBlockerService.updateOverlayState] — so that any residual
 *     sticky block state cannot render the overlay on top of our own UI.
 *
 * The counter approach (increment on ActivityStarted, decrement on
 * ActivityStopped) is robust to configuration changes, multi-window mode
 * and activity-to-activity navigation, because between the outgoing
 * activity's onStop and the incoming activity's onStart the count is at
 * worst momentarily zero — and that window is on the UI thread itself,
 * never observed by the camera callback (which runs on the service
 * handler thread).
 */
class CamShield : Application() {

    companion object {
        /**
         * `true` while at least one of our activities is in the
         * `STARTED` state (i.e. visible to the user, possibly behind a
         * translucent dialog). Volatile because it is read from the
         * camera callback's thread.
         */
        @Volatile
        var isOwnAppInForeground: Boolean = false
            private set
    }

    private var startedActivities: Int = 0

    override fun onCreate() {
        super.onCreate()

        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
            override fun onActivityStarted(activity: Activity) {
                startedActivities++
                isOwnAppInForeground = startedActivities > 0
            }
            override fun onActivityResumed(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivityStopped(activity: Activity) {
                if (startedActivities > 0) startedActivities--
                isOwnAppInForeground = startedActivities > 0
            }
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        })
    }
}