package com.sierra.camblock.activity

import android.app.ActivityManager
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.WindowInsetsCompat
import com.sierra.camblock.CameraBlockerService
import com.sierra.camblock.R
import com.sierra.camblock.databinding.ActivityCameraDisabledBinding
import com.sierra.camblock.utils.DeviceUtils
import com.sierra.camblock.utils.PrefsManager
import com.sierra.camblock.utils.getTimeFormat

class CameraDisabledActivity : AppCompatActivity() {
    private lateinit var binding : ActivityCameraDisabledBinding
    private lateinit var prefsManager: PrefsManager
    private var visitorId: String = ""
    private var hasPromptedBatteryOptimization: Boolean = false

    private val batteryOptimizationLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (isIgnoringBatteryOptimizations()) {
            Toast.makeText(this, "Battery optimization disabled for Cam Shield.", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Battery optimization permission is still pending.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityCameraDisabledBinding.inflate(layoutInflater)
        setContentView(binding.root)
        prefsManager = PrefsManager(this)
        applySystemBarsAppearance()
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        intent.getStringExtra("visitorId")?.let {
            visitorId = it
        }
        binding.tvVisitorID.text = visitorId
        initFields()
        initClickListeners()
        ensureBatteryOptimizationPermission()
    }

    private fun applySystemBarsAppearance() {
        val systemBarColor = Color.parseColor("#0B101F")
        window.statusBarColor = systemBarColor
        window.navigationBarColor = systemBarColor
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }
    }

    override fun onResume() {
        super.onResume()
        ensureBatteryOptimizationPermission()
    }

    fun isKioskModeActive(): Boolean {
        val activityManager = getSystemService(ACTIVITY_SERVICE) as ActivityManager

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // Returns LOCK_TASK_MODE_LOCKED (Device Owner)
            // or LOCK_TASK_MODE_PINNED (Standard App/Screen Pinning)
            val state = activityManager.lockTaskModeState
            state != ActivityManager.LOCK_TASK_MODE_NONE
        } else {
            // Deprecated but still works for older devices
            activityManager.isInLockTaskMode
        }
    }
    fun showCustomNoInternetDialog(context: Context) {
        // 1. Create the Dialog object
        val dialog = Dialog(context)

        // 2. Set the custom layout
        dialog.setContentView(R.layout.dialog_no_internet)

        // 3. Make the background transparent so the rounded corners (if any) show up
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        // 4. Handle the button click
        val btnOk = dialog.findViewById<Button>(R.id.btnOk)
        btnOk.setOnClickListener {
            dialog.dismiss()
        }

        // 5. Prevent closing when clicking outside
        dialog.setCancelable(false)

        dialog.show()
    }
    private fun initClickListeners() {
        binding.btnScanEntry.setOnClickListener {
            if (!DeviceUtils.isInternetAvailable(this)){
                showCustomNoInternetDialog(this)
            }else{
                val intent = Intent(this, ScanActivity::class.java)
                intent.putExtra("SCAN_ACTION", "EXIT")
                startActivity(intent)
            }

        }

        // This back press dispatcher is implemented for suppressed the toast message,
        // which is displayed when user press back while kiosk mode is activated
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (!isKioskModeActive()) {
                    finish()
                }
            }
        })
    }

    private fun initFields() {
        binding.iToolbar.toolbarTitle.text = "Active Session"
        binding.iToolbar.btnBack.visibility = View.GONE
        binding.tvEntryTime.text = getTimeFormat(this, prefsManager.entryTime)
        if (DeviceUtils.isTargetedXiaomiVersion()) {
            // Apply the "Lock" to prevent 'Clear All' button access
            try {
                // Note: If NOT Device Owner, this triggers standard Screen Pinning
                startLockTask()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        } else {
            val serviceIntent = Intent(this, CameraBlockerService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
        }
    }

    private fun ensureBatteryOptimizationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        if (isIgnoringBatteryOptimizations() || hasPromptedBatteryOptimization) return

        hasPromptedBatteryOptimization = true
        AlertDialog.Builder(this)
            .setTitle("Battery Optimization Permission")
            .setMessage("Allow Cam Shield to ignore battery optimization so background protection remains active during your session.")
            .setPositiveButton("Grant") { _, _ ->
                requestBatteryOptimizationPermission()
            }
            .setNegativeButton("Later", null)
            .setCancelable(false)
            .show()
    }

    private fun requestBatteryOptimizationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return

        val requestIntent = Intent(
            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            android.net.Uri.parse("package:$packageName")
        )

        try {
            batteryOptimizationLauncher.launch(requestIntent)
        } catch (e: Exception) {
            val fallbackIntent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            batteryOptimizationLauncher.launch(fallbackIntent)
        }
    }

    private fun isIgnoringBatteryOptimizations(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        return powerManager.isIgnoringBatteryOptimizations(packageName)
    }

}