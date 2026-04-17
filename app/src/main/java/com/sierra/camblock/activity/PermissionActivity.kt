package com.sierra.camblock.activity

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.sierra.camblock.R
import com.sierra.camblock.databinding.ActivityPermissionBinding

class PermissionActivity : AppCompatActivity() {
    private lateinit var binding: ActivityPermissionBinding

    private val settingsLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        updateAllSwitches()
        updateContinueButton()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityPermissionBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        setupSwitches()
        updateAllSwitches()
        updateContinueButton()

        binding.btnContinue.setOnClickListener {
            if (allPermissionsGranted()) {
                startActivity(Intent(this, MainActivity::class.java))
                finish()
            } else {
                val pendingPermissions = getPendingPermissionsList()
                Toast.makeText(this, "Please grant $pendingPermissions permission(s)", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupSwitches() {
        binding.swUsageStat.setOnClickListener {
            if (hasUsageStatsPermission()) {
                binding.swUsageStat.isChecked = true
                updateContinueButton()
            } else {
                binding.swUsageStat.isChecked = false
                requestUsageStatsPermission()
            }
        }

        binding.swOverlay.setOnClickListener {
            if (hasOverlayPermission()) {
                binding.swOverlay.isChecked = true
                updateContinueButton()
            } else {
                binding.swOverlay.isChecked = false
                requestOverlayPermission()
            }
        }
    }

    private fun updateAllSwitches() {
        binding.swUsageStat.isChecked = hasUsageStatsPermission()
        binding.swOverlay.isChecked = hasOverlayPermission()
    }

    private fun updateContinueButton() {
        val allGranted = allPermissionsGranted()
        binding.btnContinue.apply {
            isEnabled = allGranted
            backgroundTintList = ContextCompat.getColorStateList(
                this@PermissionActivity,
                if (allGranted) R.color.btn_blue else R.color.btn_disabled
            )
        }
    }

    private fun allPermissionsGranted(): Boolean {
        return hasUsageStatsPermission() && hasOverlayPermission()
    }

    private fun getPendingPermissionsList(): String {
        val pending = mutableListOf<String>()
        if (!hasUsageStatsPermission()) pending.add("Usage Stats")
        if (!hasOverlayPermission()) pending.add("Overlay")
        return pending.joinToString(", ")
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

    private fun requestUsageStatsPermission() {
        val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
            data = Uri.fromParts("package", packageName, null)
        }
        settingsLauncher.launch(intent)
    }

    private fun requestOverlayPermission() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.fromParts("package", packageName, null)
        )
        settingsLauncher.launch(intent)
    }

    override fun onResume() {
        super.onResume()
        updateAllSwitches()
        updateContinueButton()
    }
}