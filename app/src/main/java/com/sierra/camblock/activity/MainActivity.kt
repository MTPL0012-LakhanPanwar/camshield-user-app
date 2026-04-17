package com.sierra.camblock.activity

import android.Manifest
import android.app.ActivityManager
import android.app.AppOpsManager
import android.app.Dialog
import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.provider.Settings
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.sierra.camblock.CameraBlockerService
import com.sierra.camblock.R
import com.sierra.camblock.api.RetrofitClient
import com.sierra.camblock.api.models.DeviceInfo
import com.sierra.camblock.api.models.ScanEntryRequest
import com.sierra.camblock.api.models.ScanExitRequest
import com.sierra.camblock.databinding.ActivityMainBinding
import com.sierra.camblock.manager.DeviceAdminManager
import com.sierra.camblock.utils.Constants
import com.sierra.camblock.utils.DeviceUtils
import com.sierra.camblock.utils.PrefsManager
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.IOException

class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MainActivity"
    }

    // ViewBinding
    private lateinit var binding: ActivityMainBinding

    // Managers
    private lateinit var deviceAdminManager: DeviceAdminManager
    private lateinit var prefsManager: PrefsManager

    // Current QR scan action
    private var currentScanAction: ScanAction = ScanAction.NONE
    private enum class ScanAction { NONE, ENTRY, EXIT }
    private var openEntryScanAfterSettings: Boolean = false

    private val entryCameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            launchEntryScanActivity()
        } else if (!shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) {
            showEntryCameraSettingsDialog()
        } else {
            Toast.makeText(this, "Camera permission is required to scan entry QR.", Toast.LENGTH_SHORT).show()
        }
    }

    private val entryCameraSettingsLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (openEntryScanAfterSettings &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        ) {
            launchEntryScanActivity()
        }
        openEntryScanAfterSettings = false
    }

    private val scanOnlyResultLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val scannedValue = result.data?.getStringExtra(ScanActivity.EXTRA_SCANNED_QR)
        if (result.resultCode == RESULT_OK && !scannedValue.isNullOrBlank()) {
            handleScanResult(scannedValue)
        } else {
            Toast.makeText(this, "Scan Cancelled", Toast.LENGTH_SHORT).show()
            if (currentScanAction == ScanAction.EXIT) {
                deviceAdminManager.lockCamera()
                updateUI()
            }
            currentScanAction = ScanAction.NONE
        }
    }


    // ==================== Lifecycle Methods ====================

    override fun onCreate(savedInstanceState: Bundle?) {
        // Switch back to normal theme from splash theme
        setTheme(R.style.Theme_CameraLockDemo)
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val adminComponent = ComponentName(this, DeviceAdminReceiver::class.java)

        try {
            if (dpm.isDeviceOwnerApp(packageName)) {
                // This makes your app "Un-killable" by the user.
                // The "Force Stop" button will be greyed out, and
                // "Clear All" will fail to terminate your service.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    dpm.setUserControlDisabledPackages(adminComponent, arrayListOf(packageName))
                }
                Log.d("MDM", "User control disabled for $packageName")
            }
        } catch (e: Exception) {
            Log.e("MDM", "Failed to disable user control", e)
        }
        // Initialize managers
        deviceAdminManager = DeviceAdminManager(this)
        prefsManager = PrefsManager(this)

        applySystemBarsAppearance()
        setupWindowInsets()
        setupClickListeners()
    }

    private fun applySystemBarsAppearance() {
        val systemBarColor = ContextCompat.getColor(this, R.color.parent_bg)
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

    private fun setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

    private fun handleEntryScanClick() {
        val hasCameraPermission = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED

        if (hasCameraPermission) {
            launchEntryScanActivity()
            return
        }

        if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.CAMERA)) {
            showEntryCameraRationaleDialog()
        } else {
            entryCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun showEntryCameraRationaleDialog() {
        AlertDialog.Builder(this)
            .setTitle("Camera Permission Required")
            .setMessage("Camera access is required to scan your entry QR code.")
            .setPositiveButton("Grant") { _, _ ->
                entryCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showEntryCameraSettingsDialog() {
        AlertDialog.Builder(this)
            .setTitle("Camera Permission Denied")
            .setMessage("Please enable camera permission in app settings to scan entry QR.")
            .setPositiveButton("Open Settings") { _, _ ->
                openEntryScanAfterSettings = true
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", packageName, null)
                }
                entryCameraSettingsLauncher.launch(intent)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun launchEntryScanActivity() {
        val intent = Intent(this, ScanActivity::class.java)
        intent.putExtra("SCAN_ACTION", "ENTRY")
        startActivity(intent)
    }

    private fun showSettingsRedirectDialog(message: String) {
        AlertDialog.Builder(this)
            .setTitle("Permission Required")
            .setMessage(message)
            .setPositiveButton("Go to Settings") { _, _ ->
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                val uri = Uri.fromParts("package", packageName, null)
                intent.data = uri
                startActivity(intent)
            }
            .setNegativeButton("Exit App") { _, _ -> finish() }
            .setCancelable(false)
            .show()
    }

    private fun showCameraRationaleDialog() {
        AlertDialog.Builder(this)
            .setTitle("Camera Access Required")
            .setMessage("Cam Shield requires camera access to scan QR codes for Entry and Exit.")
            .setPositiveButton("Try Again") { _, _ ->
                isWaitingForPermission = true
                requestPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
            .setNegativeButton("Cancel", null) // Stops the flow safely
            .setCancelable(false)
            .show()
    }

    private fun checkAndRequestNextPermission(): Boolean {
        // 1. Camera - System handled
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            if (!isWaitingForPermission) {
                if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.CAMERA)) {
                    showCameraRationaleDialog()
                } else {
                    isWaitingForPermission = true
                    requestPermissionLauncher.launch(Manifest.permission.CAMERA)
                }
            }
            return false
        }

        // 2. Overlay - Only check if we haven't granted it yet AND we haven't flagged it true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            if (!prefsManager.isOverlayPermit) {
                showPermissionDialog(
                    "Overlay Permission Required",
                    "Step 2: Enable 'Display over other apps' to secure the camera.",
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    "OVERLAY", // Pass the type
                    Uri.parse("package:$packageName")
                )
                return false
            }
        }

        // 3. Usage Stats - Only check if Overlay is done and Usage is  not yet flagged
        if (!hasUsageStatsPermission()) {
            if (!prefsManager.isUsageStatPermit) {
                showPermissionDialog(
                    "Usage Access Required",
                    "Final Step: Enable 'Usage Access' to detect camera activity.",
                    Settings.ACTION_USAGE_ACCESS_SETTINGS,
                    "USAGE" // Pass the type
                )
                return false
            }
        }

        // 4. Xiaomi - Same logic
        if (isXiaomi() && !prefsManager.isXiaomiSetupDone) {
            showXiaomiPermissionDialog()
            return false
        }

        return true // All gates passed
    }
    // ==================== UI Initialization ====================
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
    private fun setupClickListeners() {
        binding.btnScanEntry.setOnClickListener {
            if (!DeviceUtils.isInternetAvailable(this)){
                showCustomNoInternetDialog(this)
            }else{
                handleEntryScanClick()
            }
        }
        binding.btnScanExit.setOnClickListener {
            // Temporarily unlock camera to allow scanning
            if (deviceAdminManager.isCameraLocked()) {
                deviceAdminManager.unlockCamera()
            }

            if (checkAndRequestNextPermission()) {
                currentScanAction = ScanAction.EXIT
                startQRScan()
            }
        }
        binding.iToolbar.btnHelp.setOnClickListener {
            startActivity(Intent(this, HelpActivity::class.java))
        }
        binding.iToolbar.btnHelp.visibility = View.VISIBLE
        binding.iToolbar.btnBack.visibility = View.GONE
        binding.iToolbar.toolbarTitle.visibility = View.GONE
    }

    private fun updateUI() {
        // Updated logic: Check Hardware Lock, Service Lock, AND Persistent Intent
        val isHardwareLocked = deviceAdminManager.isCameraLocked()
        val isServiceLocked = isServiceRunning()
        val isPersistentlyLocked = prefsManager.isLocked

        val isLocked = isHardwareLocked || isServiceLocked || isPersistentlyLocked
        val isAdmin = deviceAdminManager.isDeviceAdminActive()

        // Colors
        val colorLocked = ContextCompat.getColor(this, R.color.state_locked)
        val colorUnlocked = ContextCompat.getColor(this, R.color.btn_blue)

        if (isLocked) {
            // LOCKED STATE
            binding.tvStatus.text = "LOCKED"
            binding.tvStatus.setTextColor(colorLocked)
            binding.ivStatusIcon.setColorFilter(colorLocked)
            binding.ivStatusIcon.setImageResource(R.drawable.ic_camera_off)
            /*binding.tvStatusMessage.text = if (isServiceLocked && !isHardwareLocked) {
                getString(R.string.info_camera_locked) + "\n(Service Mode Active)"
            } else {
                getString(R.string.info_camera_locked)
            }*/

            binding.btnScanEntry.visibility = View.GONE
            binding.btnScanExit.visibility = View.VISIBLE
        } else {
            // UNLOCKED STATE
            binding.tvStatus.text = "UNLOCKED"
            binding.tvStatus.setTextColor(colorUnlocked)
            binding.ivStatusIcon.setColorFilter(colorUnlocked)
            binding.ivStatusIcon.setImageResource(R.drawable.icon_lock)
            //binding.tvStatusMessage.text = getString(R.string.info_camera_unlocked)

            binding.btnScanEntry.visibility = View.VISIBLE
            binding.btnScanExit.visibility = View.GONE
        }

        // Debug info if needed
        Log.d(TAG, "UI Updated: Locked=$isLocked (HW=$isHardwareLocked, SVC=$isServiceLocked), Admin=$isAdmin")
    }

    private fun isServiceRunning(): Boolean {
        return try {
            val activityManager = getSystemService(ACTIVITY_SERVICE) as ActivityManager
            val services = activityManager.getRunningServices(Integer.MAX_VALUE)
            for (service in services) {
                if (service.service.className == "com.example.cameralockdemo.CameraBlockerService") {
                    return true
                }
            }
            false
        } catch (e: Exception) {
            Log.e(TAG, "Error checking service status", e)
            false
        }
    }


    // ==================== Camera Permission ====================

    // In Camera Launcher
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        // Doesn't matter if granted or denied here, checkAndRequest will handle the logic
        checkAndRequestNextPermission()
    }

    private fun isXiaomi(): Boolean {
        return "xiaomi".equals(Build.MANUFACTURER, ignoreCase = true) ||
                "redmi".equals(Build.MANUFACTURER, ignoreCase = true) ||
                "poco".equals(Build.MANUFACTURER, ignoreCase = true)
    }

    private fun showXiaomiPermissionDialog() {
        AlertDialog.Builder(this)
            .setTitle("Xiaomi Device Setup")
            .setMessage("On Xiaomi/Redmi devices, you MUST enable:\n\n1. Autostart\n2. Display pop-up windows while running in the background\n\nOtherwise the camera block will NOT work.")
            .setPositiveButton("Go to Settings") { _, _ ->
                prefsManager.isXiaomiSetupDone = true
                try {
                    // Try to open specific permission editor
                    val intent = Intent("miui.intent.action.APP_PERM_EDITOR")
                    intent.setClassName("com.miui.securitycenter", "com.miui.permcenter.permissions.PermissionsEditorActivity")
                    intent.putExtra("extra_pkg_name", packageName)
                    startActivity(intent)
                } catch (e: Exception) {
                    try {
                        // Fallback to Application Details
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                        intent.data = Uri.parse("package:$packageName")
                        startActivity(intent)
                    } catch (e2: Exception) {
                        Toast.makeText(this, "Could not open settings", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setCancelable(false)
            .show()
    }

    private fun showPermissionDialog(
        title: String,
        message: String,
        action: String,
        permissionType: String, // Add this to identify which one to flip
        data: Uri? = null
    ) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("Grant") { _, _ ->
                // Mark the permission as "Attempted/Accepted" immediately
                if (permissionType == "OVERLAY") prefsManager.isOverlayPermit = true
                if (permissionType == "USAGE") prefsManager.isUsageStatPermit = true

                isWaitingForPermission = true
                val intent = Intent(action)
                if (data != null) intent.data = data
                startActivity(intent)
            }
            .setNegativeButton("Cancel") { _, _ ->
                isWaitingForPermission = false
            }
            .setCancelable(false) // Better for a setup flow
            .show()
    }
    // Resume flow helper
    private var isWaitingForPermission = false

    override fun onResume() {
        super.onResume()
        //updateUI()

        if (isWaitingForPermission) {
            isWaitingForPermission = false
            checkAndRequestNextPermission() // Moves to the next permission in the list
        }
    }
    private fun startQRScan() {
        val intent = Intent(this, ScanActivity::class.java).apply {
            putExtra(ScanActivity.EXTRA_SCAN_ONLY, true)
        }
        scanOnlyResultLauncher.launch(intent)
    }

    // Handle QR Scan Result
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        // Handle Device Admin Result
        if (requestCode == Constants.DEVICE_ADMIN_REQUEST_CODE) {
            if (resultCode == RESULT_OK || deviceAdminManager.isDeviceAdminActive()) {
                // Admin granted, lock camera
                lockCamera()
            } else {
                Toast.makeText(this, "Device admin permission denied", Toast.LENGTH_SHORT).show()
            }
            return
        }

        super.onActivityResult(requestCode, resultCode, data)
    }

    private fun handleScanResult(qrContent: String) {
        // Show loading state
        setLoading(true)

        lifecycleScope.launch {
            try {
                when (currentScanAction) {
                    ScanAction.ENTRY -> processEntryScan(qrContent)
                    ScanAction.EXIT -> processExitScan(qrContent)
                    else -> {}
                }
                updateUI()
            } catch (e: Exception) {
                handleApiError(e)
            } finally {
                setLoading(false)
                currentScanAction = ScanAction.NONE
                updateUI()
            }
        }
    }

    private suspend fun processEntryScan(token: String) {
        val deviceId = DeviceUtils.getDeviceId(this)
        val deviceInfo = DeviceInfo(
            manufacturer = Build.MANUFACTURER,
            model = Build.MODEL,
            osVersion = Build.VERSION.RELEASE,
            platform = "android",
            appVersion = Constants.APP_VERSION,
            deviceName = Build.DEVICE
        )

        val request = ScanEntryRequest(
            token = token,
            deviceId = deviceId,
            deviceInfo = deviceInfo
        )

        val response = RetrofitClient.apiService.scanEntry(request)

        if (response.isSuccessful && response.body()?.status == "success") {
            // API validated -> Request Admin / Lock
            requestDeviceAdmin()
        } else {
            showErrorDialog(response.body()?.message ?: "Entry failed. Please try again.")
        }
    }

    private suspend fun processExitScan(token: String) {
        val deviceId = DeviceUtils.getDeviceId(this)

        val request = ScanExitRequest(
            token = token,
            deviceId = deviceId
        )

        val response = RetrofitClient.apiService.scanExit(request)

        if (response.isSuccessful && response.body()?.status == "success") {
            // API validated -> Unlock
            unlockAndRemoveAdmin()
        } else {
            showErrorDialog(response.body()?.message ?: "Exit failed. Please try again.")
            // Validation failed, re-lock
            deviceAdminManager.lockCamera()
            updateUI()
        }
    }

    private fun handleApiError(e: Exception) {
        val message = when (e) {
            is IOException -> Constants.ERROR_NO_INTERNET
            is HttpException -> "Server error: ${e.code()}"
            else -> e.message ?: "Unknown error occurred"
        }
        showErrorDialog(message)

        // If error occurred during Exit, re-lock
        if (currentScanAction == ScanAction.EXIT) {
            deviceAdminManager.lockCamera()
            updateUI()
        }
    }
    private fun setLoading(isLoading: Boolean) {
        binding.btnScanEntry.isEnabled = !isLoading
        binding.btnScanExit.isEnabled = !isLoading
        if (isLoading) {
            //binding.tvStatusMessage.text = "Processing..."
        } else {
            // Restore status text based on current state
            updateUI()
        }
    }


    // ==================== Business Logic ====================

    private fun requestDeviceAdmin() {
        // Only handle Device Admin here. Other permissions are handled pre-scan.
        if (!deviceAdminManager.isDeviceAdminActive()) {
            val intent = deviceAdminManager.requestDeviceAdminPermission()
            startActivityForResult(intent, Constants.DEVICE_ADMIN_REQUEST_CODE)
        } else {
            // Already admin, just lock
            lockCamera()
        }
    }

    private fun hasUsageStatsPermission(): Boolean {
        val appOps = getSystemService(APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                packageName
            )
        } else {
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun lockCamera() {
        // Persist lock state
        prefsManager.isLocked = true

        // Check if this is MIUI Android 14+ device
        val isMiui14Plus = isMiuiDevice() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE

        Log.d(TAG, "Locking camera - MIUI 14+: $isMiui14Plus")

        if (isMiui14Plus) {
            // MIUI Android 14+ specific approach
            lockCameraMiui14Plus()
        } else {
            // Standard approach for all other devices
            lockCameraStandard()
        }

        showSuccessDialog("Camera Locked", if (isMiui14Plus) {
            Constants.SUCCESS_CAMERA_LOCKED + "\n(Active via MIUI 14+ Enhanced Blocking)"
        } else {
            Constants.SUCCESS_CAMERA_LOCKED + "\n(Active via Service & Admin)"
        })
        updateUI()
    }

    private fun lockCameraStandard() {
        // 1. Try Hardware Lock (Legacy)
        var hardwareLockSuccess = false
        try {
            hardwareLockSuccess = deviceAdminManager.lockCamera()
        } catch (e: Exception) {
            Log.e(TAG, "Hardware lock failed", e)
        }

        // 2. Start Software Lock (Service) - Always start this as backup/primary
        try {
            val serviceIntent = Intent(this, CameraBlockerService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start blocker service", e)
        }
    }

    private fun lockCameraMiui14Plus() {
        Log.d(TAG, "Using MIUI 14+ specific camera locking approach")

        // 1. Try Hardware Lock (may not work but try anyway)
        try {
            deviceAdminManager.lockCamera()
            Log.d(TAG, "MIUI 14+: Hardware lock attempted")
        } catch (e: Exception) {
            Log.e(TAG, "MIUI 14+: Hardware lock failed (expected)", e)
        }

        // 2. Start Enhanced Software Lock with MIUI 14+ specific flags
        try {
            val serviceIntent = Intent(this, CameraBlockerService::class.java)
            serviceIntent.putExtra("IS_MIUI_14_PLUS", true)
            serviceIntent.putExtra("USE_AGGRESSIVE_BLOCKING", true)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }

            Log.d(TAG, "MIUI 14+: Enhanced blocker service started")
        } catch (e: Exception) {
            Log.e(TAG, "MIUI 14+: Failed to start enhanced blocker service", e)
        }

        // 3. Additional MIUI 14+ specific setup
        setupMiui14PlusBlocking()
    }

    private fun isMiuiDevice(): Boolean {
        return try {
            val manufacturer = Build.MANUFACTURER.lowercase()
            val brand = Build.BRAND.lowercase()

            manufacturer.contains("xiaomi") ||
                    manufacturer.contains("redmi") ||
                    brand.contains("xiaomi") ||
                    brand.contains("redmi") ||
                    brand.contains("mi")
        } catch (e: Exception) {
            false
        }
    }

    private fun setupMiui14PlusBlocking() {
        Log.d(TAG, "Setting up MIUI 14+ specific blocking mechanisms")

        try {
            // Request additional permissions that might help with MIUI 14+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                // Try to get usage stats permission if not already granted
                if (!hasUsageStatsPermission()) {
                    Log.w(TAG, "MIUI 14+: Usage stats permission not granted - blocking may be less effective")
                }

                // Log MIUI version for debugging
                try {
                    val miuiVersion = Class.forName("android.os.SystemProperties")
                        .getMethod("get", String::class.java)
                        .invoke(null, "ro.miui.ui.version.name") as? String
                    Log.d(TAG, "MIUI 14+: Detected MIUI version: $miuiVersion")
                } catch (e: Exception) {
                    Log.d(TAG, "MIUI 14+: Could not detect MIUI version")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "MIUI 14+: Setup failed", e)
        }
    }

    private fun unlockAndRemoveAdmin() {
        // Clear lock state
        prefsManager.isLocked = false

        // 1. Stop Software Lock (Service)
        stopService(Intent(this, CameraBlockerService::class.java))

        // 2. Unlock Hardware
        if (deviceAdminManager.unlockCamera()) {
            // Try to remove admin
            if (deviceAdminManager.removeDeviceAdmin()) {
                showSuccessDialog("Camera Unlocked", Constants.SUCCESS_CAMERA_UNLOCKED)
            } else {
                showSuccessDialog("Camera Unlocked", "You are checked out. Please manually remove device admin permission if prompted.")
            }
            updateUI()
        } else {
            // Even if hardware unlock fails (maybe it wasn't locked), we stopped the service, so we are good.
            showSuccessDialog("Camera Unlocked", Constants.SUCCESS_CAMERA_UNLOCKED)
            updateUI()
        }
    }


    private fun showSuccessDialog(title: String, message: String) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("OK", null)
            .setIcon(R.drawable.logo_jabil)
            .show()
    }

    private fun showErrorDialog(message: String) {
        AlertDialog.Builder(this)
            .setTitle("Error")
            .setMessage(message)
            .setPositiveButton("OK", null)
            .setIcon(android.R.drawable.ic_dialog_alert)
            .show()
    }
}