package com.sierra.camblock.activity

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.sierra.camblock.R
import com.sierra.camblock.databinding.ActivitySplashBinding
import com.sierra.camblock.manager.DeviceAdminManager
import com.sierra.camblock.utils.PermissionUtils
import com.sierra.camblock.utils.applyDarkSystemBars

class SplashActivity : AppCompatActivity() {
    private lateinit var binding : ActivitySplashBinding
    private lateinit var deviceAdminManager: DeviceAdminManager

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
        return PermissionUtils.isAccessibilityServiceEnabled(this) &&
                PermissionUtils.hasOverlayPermission(this)
    }
}