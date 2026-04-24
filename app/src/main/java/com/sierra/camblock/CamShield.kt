package com.sierra.camblock

import android.app.Activity
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.os.Bundle
import java.util.concurrent.atomic.AtomicInteger

class CamShield : Application() {

    companion object {
        private const val CHANNEL_ID = "camshield_notifications"
        private const val CHANNEL_NAME = "CamShield Notifications"
        private val startedActivityCount = AtomicInteger(0)

        fun isAppInForeground(): Boolean {
            return startedActivityCount.get() > 0
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannelIfNeeded()

        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit

            override fun onActivityStarted(activity: Activity) {
                startedActivityCount.incrementAndGet()
            }

            override fun onActivityResumed(activity: Activity) = Unit

            override fun onActivityPaused(activity: Activity) = Unit

            override fun onActivityStopped(activity: Activity) {
                val updated = startedActivityCount.decrementAndGet()
                if (updated < 0) {
                    startedActivityCount.set(0)
                }
            }

            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit

            override fun onActivityDestroyed(activity: Activity) = Unit
        })
    }

    private fun createNotificationChannelIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Notifications for CamShield force exit requests and status updates"
            enableLights(true)
            enableVibration(true)
        }

        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
    }
}