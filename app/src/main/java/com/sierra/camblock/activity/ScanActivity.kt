package com.sierra.camblock.activity

import android.Manifest
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.ActivityManager
import android.app.AppOpsManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.util.Log
import android.view.animation.LinearInterpolator
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.gson.Gson
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
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
import com.sierra.camblock.utils.applyDarkSystemBarsColor
import android.graphics.Color
import com.sierra.camblock.R
import com.sierra.camblock.databinding.ActivityScanBinding
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.IOException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class ScanActivity : AppCompatActivity() {
    companion object {
        const val EXTRA_SCAN_ONLY = "EXTRA_SCAN_ONLY"
        const val EXTRA_SCANNED_QR = "EXTRA_SCANNED_QR"
    }

    private lateinit var binding : ActivityScanBinding
    private var currentScanAction: ScanAction = ScanAction.NONE
    private var visitorId: String = ""
    private var isScanOnlyMode: Boolean = false
    private enum class ScanAction { NONE, ENTRY, EXIT }
    private lateinit var deviceAdminManager: DeviceAdminManager
    private lateinit var prefsManager: PrefsManager
    private lateinit var cameraExecutor: ExecutorService
    private var cameraProvider: ProcessCameraProvider? = null
    private var isCameraStarting: Boolean = false
    private var scanningLineAnimator: ObjectAnimator? = null
    private val isScanHandled = AtomicBoolean(false)
    private val barcodeScanner by lazy {
        val options = BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .build()
        BarcodeScanning.getClient(options)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityScanBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyDarkSystemBarsColor(Color.parseColor("#0B101F"))
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        deviceAdminManager = DeviceAdminManager(this)
        prefsManager = PrefsManager(this)
        cameraExecutor = Executors.newSingleThreadExecutor()
        binding.iToolbar.toolbarTitle.text = "SCAN QR"
        binding.iToolbar.btnBack.setOnClickListener {
            handleScanCancelledOrFailed(showCancelledToast = false)
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                handleScanCancelledOrFailed(showCancelledToast = false)
            }
        })

        isScanOnlyMode = intent.getBooleanExtra(EXTRA_SCAN_ONLY, false)
        if (!isScanOnlyMode) {
            val action = intent.getStringExtra("SCAN_ACTION")
            currentScanAction = when (action) {
                "ENTRY" -> ScanAction.ENTRY
                "EXIT" -> ScanAction.EXIT
                else -> ScanAction.NONE
            }
        }

        startScanningLineAnimation()
        if (hasCameraPermission()) {
            startCameraScanner()
        } else {
            handleMissingCameraPermission()
        }

    }

    private fun hasCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun handleMissingCameraPermission() {
        showErrorDialog("Camera permission is required to scan QR codes.")
        handleScanCancelledOrFailed(showCancelledToast = false)
    }

    private fun startCameraScanner() {
        if (isScanHandled.get() || isCameraStarting || cameraProvider != null) return
        if (!isScanOnlyMode && currentScanAction == ScanAction.NONE) {
            handleScanCancelledOrFailed(showCancelledToast = false)
            return
        }

        isCameraStarting = true
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            try {
                val provider = cameraProviderFuture.get()
                cameraProvider = provider

                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(binding.previewView.surfaceProvider)
                }

                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also { analysis ->
                        analysis.setAnalyzer(cameraExecutor) { imageProxy ->
                            analyzeFrame(imageProxy)
                        }
                    }

                provider.unbindAll()
                provider.bindToLifecycle(
                    this,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    imageAnalysis
                )
                isCameraStarting = false
            } catch (e: Exception) {
                isCameraStarting = false
                Log.e(javaClass.name, "Failed to start camera scanner", e)
                showErrorDialog("Unable to start camera scanner.")
                handleScanCancelledOrFailed(showCancelledToast = false)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun analyzeFrame(imageProxy: ImageProxy) {
        if (isScanHandled.get()) {
            imageProxy.close()
            return
        }

        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }

        val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        barcodeScanner.process(image)
            .addOnSuccessListener { barcodes ->
                val rawValue = barcodes.firstNotNullOfOrNull { barcode ->
                    barcode.rawValue?.trim()?.takeIf { it.isNotEmpty() }
                }

                if (rawValue != null && isScanHandled.compareAndSet(false, true)) {
                    runOnUiThread {
                        onQrValueDetected(rawValue)
                    }
                }
            }
            .addOnFailureListener { error ->
                Log.e(javaClass.name, "QR frame analysis failed", error)
            }
            .addOnCompleteListener {
                imageProxy.close()
            }
    }

    private fun onQrValueDetected(rawValue: String) {
        stopCameraScanner()

        if (isScanOnlyMode) {
            val resultIntent = Intent().apply {
                putExtra(EXTRA_SCANNED_QR, rawValue)
            }
            setResult(RESULT_OK, resultIntent)
            finish()
            return
        }

        Log.d("QR_SCANNER", "QR Code scanned - Raw content: $rawValue")
        handleScanResult(rawValue)
    }

    private fun stopCameraScanner() {
        isCameraStarting = false
        cameraProvider?.unbindAll()
        cameraProvider = null
    }

    private fun handleScanCancelledOrFailed(showCancelledToast: Boolean) {
        stopCameraScanner()
        if (showCancelledToast) {
            Toast.makeText(this, "Scan Cancelled", Toast.LENGTH_SHORT).show()
        }

        if (isScanOnlyMode) {
            setResult(RESULT_CANCELED)
            finish()
            return
        }

        if (currentScanAction == ScanAction.EXIT) {
            deviceAdminManager.lockCamera()
        }
        finish()
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
        if (scanningLineAnimator?.isPaused == true) {
            scanningLineAnimator?.resume()
        }
        if (!isScanHandled.get() && hasCameraPermission() && cameraProvider == null) {
            startCameraScanner()
        }
    }

    override fun onPause() {
        super.onPause()
        scanningLineAnimator?.pause()
        stopCameraScanner()
    }

    override fun onDestroy() {
        super.onDestroy()
        scanningLineAnimator?.cancel()
        stopCameraScanner()
        barcodeScanner.close()
        if (::cameraExecutor.isInitialized) {
            cameraExecutor.shutdown()
        }
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

        val response = RetrofitClient.apiService.scanExit(request)
        if (response.isSuccessful) {
            // This handles 200-299 OK
            val successBody = response.body()
            if (successBody?.status == "success") {
                unlockAndRemoveAdmin()
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
        prefsManager.activeVisitorId = ""
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
        val effectiveVisitorId = if (visitorId.isNotBlank()) visitorId else prefsManager.activeVisitorId
        val intent = Intent(this, CameraDisabledActivity::class.java).apply {
            // These flags clear the entire task stack and make this the new root
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("visitorId", effectiveVisitorId)
        }
        startActivity(intent)
        finish()
    }

    private suspend fun processEntryScan(token: String) {
        Log.d("SCAN_ENTRY", "processEntryScan called with token: $token")
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
        Log.d("SCAN_ENTRY", "Calling scanEntry API with token: $token, deviceId: $deviceId")

        try {
            val response = RetrofitClient.apiService.scanEntry(request)
            Log.d("SCAN_ENTRY", "scanEntry API response received - isSuccessful: ${response.isSuccessful}, code: ${response.code()}")

            if (response.isSuccessful) {
                val apiBody = response.body()
                if (apiBody?.status == "success") {
                    // Success! Proceed to Admin request
                    visitorId = apiBody.data?.visitorId ?: ""
                    prefsManager.activeVisitorId = visitorId
                    requestDeviceAdmin()
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
            // Handle network failures (No internet, Timeout) or any other
            // unexpected error. Use a fixed LOG tag ("CamShield.Scan") because
            // `javaClass.name` is obfuscated in release and impossible to
            // grep for in logcat. Surface the exception *type* to the user
            // so field failures are self-diagnosing without needing a USB
            // logcat attach — e.g. "UnknownHostException" vs
            // "JsonSyntaxException" vs "SSLHandshakeException".
            val type = e.javaClass.simpleName
            Log.e("CamShield.Scan", "scanEntry failed: $type - ${e.message}", e)
            showErrorDialog("Network error ($type). Please try again.")
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