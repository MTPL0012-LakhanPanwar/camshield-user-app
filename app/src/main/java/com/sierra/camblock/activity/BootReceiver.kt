package com.sierra.camblock.activity

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.sierra.camblock.CameraBlockerService
import com.sierra.camblock.utils.PrefsManager

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action == Intent.ACTION_BOOT_COMPLETED || action == "android.intent.action.QUICKBOOT_POWERON") {
            val prefs = PrefsManager(context)
            if (prefs.isLocked) {
                Log.d("BootReceiver", "Boot action=$action and camera is locked. Starting service.")

                // Start the blocker service
                val serviceIntent = Intent(context, CameraBlockerService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
            }
        }
    }
}