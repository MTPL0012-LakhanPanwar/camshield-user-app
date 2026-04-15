package com.camshield.activity

import android.Manifest
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.camshield.R
import com.camshield.databinding.ActivitySplashBinding
import com.camshield.manager.DeviceAdminManager

class SplashActivity : AppCompatActivity() {
    private lateinit var binding : ActivitySplashBinding
    private lateinit var deviceAdminManager: DeviceAdminManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        deviceAdminManager = DeviceAdminManager(this)

        Handler(Looper.getMainLooper()).postDelayed({
            navigateToAppropriateScreen()
        }, 3000)
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
        return hasCameraPermission() &&
                hasUsageStatsPermission() &&
                hasOverlayPermission() &&
                isIgnoringBatteryOptimizations()
    }

    private fun hasCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this, Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
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

    private fun isIgnoringBatteryOptimizations(): Boolean {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        return powerManager.isIgnoringBatteryOptimizations(packageName)
    }
}