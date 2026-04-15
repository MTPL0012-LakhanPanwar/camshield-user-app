package com.camshield

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.hardware.camera2.CameraManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.Button
import androidx.core.app.NotificationCompat
import com.camshield.utils.PrefsManager

class CameraBlockerService : Service() {

    companion object {
        var isServiceRunning = false
    }

    private val handler = Handler(Looper.getMainLooper())
    private val checkInterval = 200L // Check every 200ms (Aggressive)
    private var isRunning = false
    private lateinit var cameraManager: CameraManager
    private lateinit var prefsManager: PrefsManager
    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var isOverlayShowing = false

    // State tracking
    private var isCameraInUse = false // From Callback
    private var isCameraAppForeground = false // From Usage Stats

    private val CAMERA_PACKAGES = listOf(
        "com.android.camera",
        "com.google.android.GoogleCamera",
        "com.samsung.android.camera",
        "com.sec.android.app.camera",
        "com.xiaomi.camera",
        "com.huawei.camera",
        "com.oppo.camera",
        "com.oneplus.camera",
        "com.motorola.camera2",
        "com.asus.camera",
        "com.sonyericsson.android.camera",
        "org.codeaurora.snapcam"
    )

    private val runnable = object : Runnable {
        override fun run() {
            if (!isRunning) return
            checkForegroundApp()
            updateOverlayState()
            handler.postDelayed(this, checkInterval)
        }
    }

    private val cameraCallback = object : CameraManager.AvailabilityCallback() {
        override fun onCameraUnavailable(cameraId: String) {
            super.onCameraUnavailable(cameraId)
            // Camera is being used by SOME app.
            isCameraInUse = true
            Log.d("CameraBlocker", "Camera Unavailable (In Use)")
            updateOverlayState()
        }

        override fun onCameraAvailable(cameraId: String) {
            super.onCameraAvailable(cameraId)
            // Camera is free.
            isCameraInUse = false
            updateOverlayState()
        }
    }

    override fun onCreate() {
        super.onCreate()
        prefsManager = PrefsManager(this)
        cameraManager = getSystemService(CAMERA_SERVICE) as CameraManager
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        // Register Camera Callback
        try {
            cameraManager.registerAvailabilityCallback(cameraCallback, handler)
        } catch (e: Exception) {
            Log.e("CameraBlocker", "Failed to register camera callback", e)
        }

        startForegroundService()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!isRunning) {
            isRunning = true
            isServiceRunning = true
            handler.post(runnable)
        }
        return START_STICKY
    }

    override fun onDestroy() {
        isRunning = false
        isServiceRunning = false
        handler.removeCallbacks(runnable)
        hideOverlay() // Ensure overlay is removed
        try {
            cameraManager.unregisterAvailabilityCallback(cameraCallback)
        } catch (e: Exception) {
            Log.e("CameraBlocker", "Failed to unregister camera callback", e)
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private fun startForegroundService() {
        val channelId = "CameraBlockerChannel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Camera Blocker Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Camera Lock Active")
            .setContentText("Monitoring for camera usage...")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .build()

        startForeground(1, notification)
    }

    private fun checkForegroundApp() {
        val usageStatsManager = getSystemService(USAGE_STATS_SERVICE) as UsageStatsManager
        val time = System.currentTimeMillis()
        var currentApp = ""

        // Strategy 1: Usage Events (Precise)
        val events = usageStatsManager.queryEvents(time - 1000, time) // 2 seconds window
        val event = UsageEvents.Event()

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                currentApp = event.packageName
            }
        }

        // Strategy 2: Usage Stats Snapshot (Fallback)
        if (currentApp.isEmpty()) {
            val stats = usageStatsManager.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY,
                time - 1000 * 10,
                time
            )
            if (stats != null && stats.isNotEmpty()) {
                val sortedStats = stats.sortedByDescending { it.lastTimeUsed }
                if (sortedStats.isNotEmpty()) {
                    currentApp = sortedStats[0].packageName
                }
            }
        }

        if (currentApp.isNotEmpty()) {
            // Update state
            val wasForeground = isCameraAppForeground
            isCameraAppForeground = isCameraApp(currentApp)

            if (wasForeground != isCameraAppForeground) {
                Log.d("CameraBlocker", "Foreground App Changed: $currentApp (IsCameraApp: $isCameraAppForeground)")
            }
        }
    }

    private fun updateOverlayState() {
        if (!prefsManager.isLocked) {
            hideOverlay()
            return
        }

        // Don't block our own app - CHECKING EVENTS NOW for robust foreground detection
        if (isAppForeground(packageName)) {
            hideOverlay()
            return
        }

        if (isCameraInUse || isCameraAppForeground) {
            showOverlay()
        } else {
            hideOverlay()
        }
    }

    private fun showOverlay() {
        if (isOverlayShowing) return

        // Double check Overlay Permission
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            Log.e("CameraBlocker", "Cannot show overlay: Permission missing")
            return
        }

        try {
            if (overlayView == null) {
                val inflater = getSystemService(LAYOUT_INFLATER_SERVICE) as LayoutInflater
                overlayView = inflater.inflate(R.layout.activity_blocked, null)

                // Set up dismiss button
                val btnDismiss = overlayView?.findViewById<Button>(R.id.btnDismiss)
                btnDismiss?.setOnClickListener {
                    // Send user to Home Screen
                    val startMain = Intent(Intent.ACTION_MAIN)
                    startMain.addCategory(Intent.CATEGORY_HOME)
                    startMain.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    startActivity(startMain)
                }
            }

            // Flags updated for better compatibility (Xiaomi, etc.)
            val layoutParams = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                else
                    WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                        WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or // Allow extending outside screen
                        WindowManager.LayoutParams.FLAG_FULLSCREEN,         // Request full screen
                PixelFormat.TRANSLUCENT
            )

            // Handle Display Cutout (Notch)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutParams.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }

            layoutParams.gravity = Gravity.CENTER

            // Hide System UI (Immersive Mode)
            overlayView?.systemUiVisibility = (View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION // hide nav bar
                    or View.SYSTEM_UI_FLAG_FULLSCREEN // hide status bar
                    or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)

            windowManager?.addView(overlayView, layoutParams)
            isOverlayShowing = true
            Log.d("CameraBlocker", "Overlay SHOWN")
        } catch (e: Exception) {
            Log.e("CameraBlocker", "Failed to show overlay", e)
        }
    }

    private fun hideOverlay() {
        if (!isOverlayShowing) return

        try {
            if (overlayView != null && windowManager != null) {
                windowManager?.removeView(overlayView)
            }
            isOverlayShowing = false
            Log.d("CameraBlocker", "Overlay HIDDEN")
        } catch (e: Exception) {
            Log.e("CameraBlocker", "Failed to hide overlay", e)
        }
    }

    // Improved isAppForeground using events first (more robust)
    private fun isAppForeground(targetPackage: String): Boolean {
        val usageStatsManager = getSystemService(USAGE_STATS_SERVICE) as UsageStatsManager
        val time = System.currentTimeMillis()

        // 1. Check Events (Fast & Accurate)
        val events = usageStatsManager.queryEvents(time - 1000, time)
        val event = UsageEvents.Event()
        var lastForegroundApp = ""
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                lastForegroundApp = event.packageName
            }
        }

        if (lastForegroundApp.isNotEmpty()) {
            return lastForegroundApp == targetPackage
        }

        // 2. Fallback to Stats (Slow but better than nothing)
        val stats = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            time - 1000 * 10,
            time
        )

        if (stats != null && stats.isNotEmpty()) {
            val sortedStats = stats.sortedByDescending { it.lastTimeUsed }
            if (sortedStats.isNotEmpty()) {
                return sortedStats[0].packageName == targetPackage
            }
        }
        return false
    }

    private fun isCameraApp(packageName: String): Boolean {
        if (packageName == applicationContext.packageName) return false
        if (CAMERA_PACKAGES.contains(packageName)) return true
        if (packageName.contains("camera", ignoreCase = true)) {
            if (packageName != applicationContext.packageName) {
                return true
            }
        }
        return false
    }
}