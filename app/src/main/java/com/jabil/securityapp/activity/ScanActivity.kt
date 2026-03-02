package com.jabil.securityapp.activity

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.ActivityManager
import android.app.AppOpsManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.util.Log
import android.view.animation.LinearInterpolator
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.animation.doOnCancel
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.google.zxing.ResultPoint
import com.google.zxing.client.android.BeepManager
import com.jabil.securityapp.CameraBlockerService
import com.jabil.securityapp.R
import com.jabil.securityapp.api.RetrofitClient
import com.jabil.securityapp.api.models.DeviceInfo
import com.jabil.securityapp.api.models.ScanEntryRequest
import com.jabil.securityapp.api.models.ScanExitRequest
import com.jabil.securityapp.databinding.ActivityScanBinding
import com.jabil.securityapp.manager.DeviceAdminManager
import com.jabil.securityapp.utils.Constants
import com.jabil.securityapp.utils.DeviceUtils
import com.jabil.securityapp.utils.PrefsManager
import com.journeyapps.barcodescanner.BarcodeCallback
import com.journeyapps.barcodescanner.BarcodeResult
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.IOException

class ScanActivity : AppCompatActivity() {
    private lateinit var binding : ActivityScanBinding
    private var currentScanAction: ScanAction = ScanAction.NONE
    private enum class ScanAction { NONE, ENTRY, EXIT }
    private lateinit var deviceAdminManager: DeviceAdminManager
    private lateinit var prefsManager: PrefsManager
    private lateinit var beepManager: BeepManager
    private var scanningLineAnimator: ObjectAnimator? = null
    private var lastScanResult: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityScanBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        deviceAdminManager = DeviceAdminManager(this)
        prefsManager = PrefsManager(this)
        beepManager = BeepManager(this)

        binding.btnBack.setOnClickListener {
            finish()
        }

        // Get scan action from intent
        val action = intent.getStringExtra("SCAN_ACTION")
        currentScanAction = when (action) {
            "ENTRY" -> ScanAction.ENTRY
            "EXIT" -> ScanAction.EXIT
            else -> ScanAction.NONE
        }

        setupBarcodeScanner()
        startScanningLineAnimation()

    }

    private fun setupBarcodeScanner() {
        binding.zxingBarcodeScanner.viewFinder.setMaskColor(android.graphics.Color.TRANSPARENT)
        binding.zxingBarcodeScanner.setStatusText("")
        binding.zxingBarcodeScanner.decodeContinuous(object : BarcodeCallback {
            override fun barcodeResult(result: BarcodeResult) {
                if (result.text != null && result.text != lastScanResult) {
                    lastScanResult = result.text
                    beepManager.playBeepSoundAndVibrate()
                    handleScanResult(result.text)
                }
            }

            override fun possibleResultPoints(resultPoints: List<ResultPoint>) {}
        })
    }

    private fun startScanningLineAnimation() {
        binding.viewFinder.post {
            val viewFinderHeight = binding.viewFinder.height.toFloat()
            val lineHeight = binding.scanningLine.height.toFloat()
            val marginPx = 10f * resources.displayMetrics.density
            val travelDistance = viewFinderHeight - lineHeight - (marginPx * 2)
            
            scanningLineAnimator = ObjectAnimator.ofFloat(
                binding.scanningLine,
                "translationY",
                0f,
                travelDistance
            ).apply {
                duration = 2000
                repeatCount = ValueAnimator.INFINITE
                repeatMode = ValueAnimator.REVERSE
                interpolator = LinearInterpolator()
                start()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        binding.zxingBarcodeScanner.resume()
        if (scanningLineAnimator?.isPaused == true) {
            scanningLineAnimator?.resume()
        }
    }

    override fun onPause() {
        super.onPause()
        binding.zxingBarcodeScanner.pause()
        scanningLineAnimator?.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        scanningLineAnimator?.cancel()
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
            } catch (e: Exception) {
                handleApiError(e)
            } finally {
                setLoading(false)
                currentScanAction = ScanAction.NONE
                lastScanResult = null
            }
        }
    }

    private fun handleApiError(e: Exception) {
        val message = when (e) {
            is IOException -> Constants.ERROR_NO_INTERNET
            is HttpException -> "Server error: ${e.code()}"
            else -> e.message ?: "Unknown error occurred"
        }
        showErrorDialog(message)

        if (currentScanAction == ScanAction.EXIT) {
            deviceAdminManager.lockCamera()
        }
    }

    private fun showErrorDialog(message: String) {
        Toast.makeText(this, "Something went wrong!", Toast.LENGTH_LONG).show()
    }

    private fun setLoading(isLoading: Boolean) {
        // Disable buttons during loading if needed
        binding.btnBack.isEnabled = !isLoading
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
            Log.e(javaClass.name, "Error checking service status", e)
            false
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
            unlockAndRemoveAdmin()
        } else {
            showErrorDialog(response.body()?.message ?: "Exit failed. Please try again.")
            deviceAdminManager.lockCamera()
        }
    }

    private fun unlockAndRemoveAdmin() {
        if (DeviceUtils.isTargetedXiaomiVersion()){
            stopLockTask()
        }
        prefsManager.isLocked = false
        stopService(Intent(this, CameraBlockerService::class.java))

        if (deviceAdminManager.unlockCamera()) {
            deviceAdminManager.removeDeviceAdmin()
        }

        val intent = Intent(this, PermissionRestoreActivity::class.java).apply {
            // These flags clear the entire task stack and make this the new root
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        finish()
    }

    private fun startCamDisabledActivity() {
        val intent = Intent(this, CameraDisabledActivity::class.java).apply {
            // These flags clear the entire task stack and make this the new root
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        finish()
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
            requestDeviceAdmin()
        } else {
            showErrorDialog(response.body()?.message ?: "Entry failed. Please try again.")
        }
    }

    private fun requestDeviceAdmin() {
        if (!deviceAdminManager.isDeviceAdminActive()) {
            val intent = deviceAdminManager.requestDeviceAdminPermission()
            startActivityForResult(intent, Constants.DEVICE_ADMIN_REQUEST_CODE)
        } else {
            lockCamera()
        }
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

    private fun lockCamera() {
        prefsManager.isLocked = true
        prefsManager.entryTime = System.currentTimeMillis()
        val isMiui14Plus = isMiuiDevice() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE

        Log.d(javaClass.name, "Locking camera - MIUI 14+: $isMiui14Plus")

        if (isMiui14Plus) {
            lockCameraMiui14Plus()
        } else {
            lockCameraStandard()
        }

        startCamDisabledActivity()
    }

    private fun lockCameraStandard() {
        var hardwareLockSuccess = false
        try {
            hardwareLockSuccess = deviceAdminManager.lockCamera()
        } catch (e: Exception) {
            Log.e(javaClass.name, "Hardware lock failed", e)
        }

        try {
            val serviceIntent = Intent(this, CameraBlockerService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
        } catch (e: Exception) {
            Log.e(javaClass.name, "Failed to start blocker service", e)
        }
    }

    private fun lockCameraMiui14Plus() {
        Log.d(javaClass.name, "Using MIUI 14+ specific camera locking approach")

        try {
            deviceAdminManager.lockCamera()
            Log.d(javaClass.name, "MIUI 14+: Hardware lock attempted")
        } catch (e: Exception) {
            Log.e(javaClass.name, "MIUI 14+: Hardware lock failed (expected)", e)
        }

        try {
            val serviceIntent = Intent(this, CameraBlockerService::class.java)
            serviceIntent.putExtra("IS_MIUI_14_PLUS", true)
            serviceIntent.putExtra("USE_AGGRESSIVE_BLOCKING", true)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }

            Log.d(javaClass.name, "MIUI 14+: Enhanced blocker service started")
        } catch (e: Exception) {
            Log.e(javaClass.name, "MIUI 14+: Failed to start enhanced blocker service", e)
        }

        setupMiui14PlusBlocking()
    }

    private fun setupMiui14PlusBlocking() {
        Log.d(javaClass.name, "Setting up MIUI 14+ specific blocking mechanisms")

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                if (!hasUsageStatsPermission()) {
                    Log.w(javaClass.name, "MIUI 14+: Usage stats permission not granted - blocking may be less effective")
                }

                try {
                    val miuiVersion = Class.forName("android.os.SystemProperties")
                        .getMethod("get", String::class.java)
                        .invoke(null, "ro.miui.ui.version.name") as? String
                    Log.d(javaClass.name, "MIUI 14+: Detected MIUI version: $miuiVersion")
                } catch (e: Exception) {
                    Log.d(javaClass.name, "MIUI 14+: Could not detect MIUI version")
                }
            }
        } catch (e: Exception) {
            Log.e(javaClass.name, "MIUI 14+: Setup failed", e)
        }
    }

    private fun hasUsageStatsPermission(): Boolean {
        val appOps = getSystemService(APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
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

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == Constants.DEVICE_ADMIN_REQUEST_CODE) {
            if (resultCode == RESULT_OK || deviceAdminManager.isDeviceAdminActive()) {
                lockCamera()
            } else {
                Toast.makeText(this, "Device admin permission denied", Toast.LENGTH_SHORT).show()
            }
            return
        }

        super.onActivityResult(requestCode, resultCode, data)
    }
}