package com.sierra.camblock.activity

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
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

        Handler(Looper.getMainLooper()).postDelayed({
            navigateToAppropriateScreen()
        }, 3000)
    }

    private fun ensureBlockerServiceRunningIfLocked() {
        if (!prefsManager.isLocked) return
        if (!allPermissionsGranted()) return

        try {
            val serviceIntent = Intent(this, CameraBlockerService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
        } catch (_: Exception) {
            // Best-effort recovery path for killed service after OEM clear-all.
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