package com.camshield.activity

import android.Manifest
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.camshield.R
import com.camshield.databinding.ActivityPermissionBinding

class PermissionActivity : AppCompatActivity() {
    private lateinit var binding: ActivityPermissionBinding

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            binding.switchCamera.isChecked = true
        } else {
            binding.switchCamera.isChecked = false
            if (!shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) {
                showCameraSettingsDialog()
            }
        }
        updateContinueButton()
    }

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
        binding.switchCamera.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked && !hasCameraPermission()) {
                requestCameraPermission()
            } else if (!isChecked) {
                updateContinueButton()
            }
        }

        binding.swUsageStat.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked && !hasUsageStatsPermission()) {
                requestUsageStatsPermission()
            } else {
                binding.swUsageStat.isChecked = hasUsageStatsPermission()
                updateContinueButton()
            }
        }

        binding.swOverlay.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked && !hasOverlayPermission()) {
                requestOverlayPermission()
            } else {
                binding.swOverlay.isChecked = hasOverlayPermission()
                updateContinueButton()
            }
        }

        binding.swBatOpti.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked && !isIgnoringBatteryOptimizations()) {
                requestBatteryOptimizationPermission()
            } else {
                binding.swBatOpti.isChecked = isIgnoringBatteryOptimizations()
                updateContinueButton()
            }
        }
    }

    private fun updateAllSwitches() {
        binding.switchCamera.isChecked = hasCameraPermission()
        binding.swUsageStat.isChecked = hasUsageStatsPermission()
        binding.swOverlay.isChecked = hasOverlayPermission()
        binding.swBatOpti.isChecked = isIgnoringBatteryOptimizations()
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
        return hasCameraPermission() &&
                hasUsageStatsPermission() &&
                hasOverlayPermission() &&
                isIgnoringBatteryOptimizations()
    }

    private fun getPendingPermissionsList(): String {
        val pending = mutableListOf<String>()
        if (!hasCameraPermission()) pending.add("Camera")
        if (!hasUsageStatsPermission()) pending.add("Usage Stats")
        if (!hasOverlayPermission()) pending.add("Overlay")
        if (!isIgnoringBatteryOptimizations()) pending.add("Battery Optimization")
        return pending.joinToString(", ")
    }

    private fun hasCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this, Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestCameraPermission() {
        when {
            shouldShowRequestPermissionRationale(Manifest.permission.CAMERA) -> {
                AlertDialog.Builder(this)
                    .setTitle("Camera Permission Required")
                    .setMessage("Camera access is needed to scan enrollment QR codes. Please grant permission to continue.")
                    .setPositiveButton("Grant") { _, _ ->
                        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                    }
                    .setNegativeButton("Cancel") { dialog, _ ->
                        dialog.dismiss()
                        binding.switchCamera.isChecked = false
                    }
                    .show()
            }
            else -> {
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }

    private fun showCameraSettingsDialog() {
        AlertDialog.Builder(this)
            .setTitle("Permission Denied")
            .setMessage("Camera permission was denied. Please enable it in app settings to scan QR codes.")
            .setPositiveButton("Open Settings") { _, _ ->
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", packageName, null)
                }
                settingsLauncher.launch(intent)
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
                binding.switchCamera.isChecked = false
            }
            .setCancelable(false)
            .show()
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
        val powerManager = getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
        return powerManager.isIgnoringBatteryOptimizations(packageName)
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

    private fun requestBatteryOptimizationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                val intent = Intent(
                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:$packageName")
                )
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(this, "Into Exception", Toast.LENGTH_SHORT).show()
            }
        }
    }
}