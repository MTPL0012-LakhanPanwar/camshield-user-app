package com.sierra.camblock.activity

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.sierra.camblock.CameraBlockerService
import com.sierra.camblock.R
import com.sierra.camblock.databinding.ActivitySplashBinding
import com.sierra.camblock.manager.DeviceAdminManager
import com.sierra.camblock.utils.PrefsManager
import com.sierra.camblock.utils.applyDarkSystemBars

class SplashActivity : AppCompatActivity() {
    companion object {
        private const val EXTRA_NOTIFICATION_DATA = "notification_data"
        private const val EXTRA_TYPE = "type"
        private const val TYPE_FORCE_EXIT_APPROVED = "FORCE_EXIT_APPROVED"
        private const val TYPE_RESTORE = "RESTORE"
    }

    private lateinit var binding : ActivitySplashBinding
    private lateinit var deviceAdminManager: DeviceAdminManager
    private lateinit var prefsManager: PrefsManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyDarkSystemBars()
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        deviceAdminManager = DeviceAdminManager(this)
        prefsManager = PrefsManager(this)

        ensureBlockerServiceRunningIfLocked()

        if (routeFromNotificationIntent(intent)) {
            return
        }

        Handler(Looper.getMainLooper()).postDelayed({
            navigateToAppropriateScreen()
        }, 3000)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        routeFromNotificationIntent(intent)
    }

    private fun ensureBlockerServiceRunningIfLocked() {
        if (!prefsManager.isLocked) return
        if (!allPermissionsGranted()) return
        if (CameraBlockerService.isServiceRunning) return

        try {
            val serviceIntent = Intent(this, CameraBlockerService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
        } catch (e: Exception) {
            // Best-effort recovery path for killed service after OEM clear-all.
            Log.w("SplashActivity", "Recovery: failed to start blocker service", e)
        }
    }

    private fun navigateToAppropriateScreen() {
        val intent = when {
            !allPermissionsGranted() -> {
                Intent(this, PermissionActivity::class.java)
            }
            deviceAdminManager.isDeviceAdminActive() -> {
                Intent(this, CameraDisabledActivity::class.java)
            }
            else -> {
                Intent(this, MainActivity::class.java)
            }
        }
        startActivity(intent)
        finish()
    }

    private fun routeFromNotificationIntent(launchIntent: Intent?): Boolean {
        if (launchIntent == null) return false

        val payload = extractNotificationPayload(launchIntent)
        val type = payload[EXTRA_TYPE]

        if (type != TYPE_FORCE_EXIT_APPROVED && type != TYPE_RESTORE) {
            return false
        }

        val restoreIntent = Intent(this, PermissionRestoreActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra(EXTRA_NOTIFICATION_DATA, HashMap(payload))
        }
        startActivity(restoreIntent)
        finish()
        return true
    }

    private fun extractNotificationPayload(launchIntent: Intent): Map<String, String> {
        val payload = mutableMapOf<String, String>()

        val bundledPayload = launchIntent.extras?.get(EXTRA_NOTIFICATION_DATA)
        if (bundledPayload is HashMap<*, *>) {
            bundledPayload.forEach { (key, value) ->
                if (key is String && value is String) {
                    payload[key] = value
                }
            }
        }

        val typeFromExtras = launchIntent.getStringExtra(EXTRA_TYPE)
            ?: launchIntent.getStringExtra("gcm.notification.type")
            ?: launchIntent.getStringExtra("gcm.n.type")

        if (!typeFromExtras.isNullOrBlank()) {
            payload[EXTRA_TYPE] = typeFromExtras
        }

        return payload
    }

    private fun allPermissionsGranted(): Boolean {
        return hasUsageStatsPermission() && hasOverlayPermission()
    }

    private fun hasUsageStatsPermission(): Boolean {
        val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun hasOverlayPermission(): Boolean {
        return Settings.canDrawOverlays(this)
    }
}