package com.sierra.camblock.activity

import android.Manifest
import android.app.ActivityManager
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
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
    companion object {
        const val EXTRA_SHOW_TOAST = "show_toast"
    }

    private lateinit var binding : ActivityCameraDisabledBinding
    private lateinit var prefsManager: PrefsManager
    private var visitorId: String = ""
    private var openExitScanAfterSettings: Boolean = false

    private val exitCameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            launchExitScanActivity()
        } else if (!shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) {
            showExitCameraSettingsDialog()
        } else {
            Toast.makeText(this, "Camera permission is required to scan exit QR.", Toast.LENGTH_SHORT).show()
        }
    }

    private val exitCameraSettingsLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (
            openExitScanAfterSettings &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        ) {
            launchExitScanActivity()
        }
        openExitScanAfterSettings = false
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
        restoreVisitorId()
        initFields()
        initClickListeners()
        maybeShowSettingsBlockedToast()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        maybeShowSettingsBlockedToast()
    }

    private fun maybeShowSettingsBlockedToast() {
        if (!intent.getBooleanExtra(EXTRA_SHOW_TOAST, false)) return
        Toast.makeText(
            this,
            "Settings access is blocked while camera lock is active.",
            Toast.LENGTH_SHORT
        ).show()
        intent.removeExtra(EXTRA_SHOW_TOAST)
    }

    private fun handleExitScanClick() {
        val hasCameraPermission = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED

        if (hasCameraPermission) {
            launchExitScanActivity()
            return
        }

        // Trigger native system permission dialog directly.
        exitCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
    }

    private fun showExitCameraSettingsDialog() {
        AlertDialog.Builder(this)
            .setTitle("Camera Permission Denied")
            .setMessage("Please enable camera permission in app settings to scan exit QR.")
            .setPositiveButton("Open Settings") { _, _ ->
                openExitScanAfterSettings = true
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", packageName, null)
                }
                exitCameraSettingsLauncher.launch(intent)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun launchExitScanActivity() {
        val intent = Intent(this, ScanActivity::class.java)
        intent.putExtra("SCAN_ACTION", "EXIT")
        startActivity(intent)
    }

    private fun restoreVisitorId() {
        val visitorIdFromIntent = intent.getStringExtra("visitorId").orEmpty()
        visitorId = if (visitorIdFromIntent.isNotBlank()) {
            prefsManager.activeVisitorId = visitorIdFromIntent
            visitorIdFromIntent
        } else {
            prefsManager.activeVisitorId
        }
        binding.tvVisitorID.text = visitorId
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
                handleExitScanClick()
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
            ensureBlockerServiceRunningIfLocked()

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

    private fun ensureBlockerServiceRunningIfLocked() {
        if (!prefsManager.isLocked) return
        if (CameraBlockerService.isServiceRunning) return

        try {
            val serviceIntent = Intent(this, CameraBlockerService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

}