package com.sierra.camblock.activity

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
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.google.gson.Gson
import com.google.zxing.ResultPoint
import com.google.zxing.client.android.BeepManager
import com.sierra.camblock.CameraBlockerService
import com.sierra.camblock.api.RetrofitClient
import com.sierra.camblock.api.models.ApiResponse
import com.sierra.camblock.api.models.DeviceInfo
import com.sierra.camblock.api.models.ScanEntryRequest
import com.sierra.camblock.api.models.ScanExitRequest
import com.sierra.camblock.manager.DeviceAdminManager
import com.sierra.camblock.utils.Constants
import com.sierra.camblock.utils.DeviceUtils
import com.sierra.camblock.utils.PrefsManager
import com.journeyapps.barcodescanner.BarcodeCallback
import com.journeyapps.barcodescanner.BarcodeResult
import com.sierra.camblock.R
import com.sierra.camblock.databinding.ActivityScanBinding
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.IOException

class ScanActivity : AppCompatActivity() {
    private lateinit var binding : ActivityScanBinding
    private var currentScanAction: ScanAction = ScanAction.NONE
    private var visitorId: String = ""
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
        binding.iToolbar.toolbarTitle.text = "SCAN QR"
        binding.iToolbar.btnBack.setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
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
                    Log.e(javaClass.name, "barcodeResult: ${result.text}")
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
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
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
        Log.e(javaClass.name, "processExitScan: $request")

        val response = RetrofitClient.apiService.scanExit(request)
        if (response.isSuccessful) {
            // This handles 200-299 OK
            val successBody = response.body()
            if (successBody?.status == "success") {
                unlockAndRemoveAdmin()
                prefsManager.visitorId = ""
            } else {
                showErrorDialog(successBody?.message ?: "Exit failed.")
                finish()
            }
        } else {
            // This handles 400, 401, 500, etc.
            // 1. Get the raw JSON string from errorBody
            val errorJsonString = response.errorBody()?.string()

            // 2. Parse that JSON string into your ApiResponse class
            val errorData = try {
                Gson().fromJson(errorJsonString, ApiResponse::class.java)
            } catch (e: Exception) {
                null
            }

            // 3. Use the message from the parsed error
            val displayMessage = errorData?.message ?: "Error: ${response.code()}"

            Log.e(javaClass.name, "Actual Server Error Message: $displayMessage")
            showErrorDialog(displayMessage)

            deviceAdminManager.lockCamera()
            finish()
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
            putExtra("visitorId", visitorId)
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
        Log.e(javaClass.name, "processEntryScan: $request")

        try {
            val response = RetrofitClient.apiService.scanEntry(request)

            if (response.isSuccessful) {
                val apiBody = response.body()
                if (apiBody?.status == "success") {
                    // Success! Proceed to Admin request
                    requestDeviceAdmin()
                    visitorId = response.body()?.data?.visitorId?: ""
                    prefsManager.visitorId = response.body()?.data?.visitorId?: ""
                } else {
                    // Server returned 200 but status was "failure" or similar
                    showErrorDialog(apiBody?.message ?: "Entry denied.")
                    finish()
                }
            } else {
                // Handle 400, 401, 500 etc. (Server Error)
                val errorJsonString = response.errorBody()?.string()

                // Parse the error JSON manually since response.body() is null here
                val errorData = try {
                    Gson().fromJson(errorJsonString, ApiResponse::class.java)
                } catch (e: Exception) {
                    null
                }

                val displayMessage = errorData?.message ?: "Server Error: ${response.code()}"
                Log.e(javaClass.name, "Entry Error: $displayMessage")

                showErrorDialog(displayMessage)
                finish()
            }
        } catch (e: Exception) {
            // Handle network failures (No internet, Timeout)
            Log.e(javaClass.name, "Network Exception: ${e.message}")
            showErrorDialog("Network error. Please try again.")
            finish()
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
                finish()
                Toast.makeText(this, "Device admin permission denied", Toast.LENGTH_SHORT).show()
            }
            return
        }

        super.onActivityResult(requestCode, resultCode, data)
    }
}