package com.sierra.camblock

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
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
import com.sierra.camblock.utils.PrefsManager

class CameraBlockerService : Service() {

    companion object {
        var isServiceRunning = false
    }

    private val handler = Handler(Looper.getMainLooper())
    private val checkInterval = 200L // Check every 200ms (Aggressive)
    private val minForegroundCheckGapMs = 500L
    private val usageStatsFallbackGapMs = 2000L
    private var isRunning = false
    private lateinit var cameraManager: CameraManager
    private lateinit var prefsManager: PrefsManager
    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var isOverlayShowing = false

    // State tracking
    private var isCameraInUse = false // From Callback
    private var isCameraAppForeground = false // From Usage Stats
    private var lastForegroundCheckAt = 0L
    private var lastUsageStatsFallbackAt = 0L
    private var cameraUnavailableAtMs = 0L

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
            cameraUnavailableAtMs = System.currentTimeMillis()
            isCameraInUse = true
            Log.d("CameraBlocker", "Camera Unavailable (In Use)")

            // Fast path: on Android 16+, usage stats foreground detection can lag.
            // Show blocker immediately when camera becomes unavailable and app is backgrounded.
            if (prefsManager.isLocked && !CamShield.isAppInForeground()) {
                showOverlay()
            } else {
                updateOverlayState()
            }
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

        prepareOverlayView()

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

        // Android 14 (API 34) enforces that the foregroundServiceType passed to
        // startForeground() matches the one declared in the manifest AND that the
        // app holds the corresponding FOREGROUND_SERVICE_* permission. The service
        // only observes camera availability (no capture), so we use specialUse.
        // Guarding with try/catch prevents a process-wide crash if the platform
        // still rejects the start (e.g. OEM restrictions, background launch denied).
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    1,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(1, notification)
            }
        } catch (e: Exception) {
            Log.e("CameraBlocker", "startForeground failed", e)
            // Stop self so the system does not keep the service in a half-started
            // state that would trigger ForegroundServiceDidNotStartInTimeException.
            stopSelf()
        }
    }

    private fun checkForegroundApp() {
        val now = System.currentTimeMillis()
        if (now - lastForegroundCheckAt < minForegroundCheckGapMs) {
            return
        }
        lastForegroundCheckAt = now

        val usageStatsManager = getSystemService(USAGE_STATS_SERVICE) as UsageStatsManager
        var currentApp = ""

        // Strategy 1: Usage Events (Precise)
        try {
            val events = usageStatsManager.queryEvents(now - 1000, now)
            val event = UsageEvents.Event()

            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                    currentApp = event.packageName
                }
            }
        } catch (e: Exception) {
            Log.w("CameraBlocker", "queryEvents failed", e)
        }

        // Strategy 2: Usage Stats Snapshot (Fallback)
        if (currentApp.isEmpty() && now - lastUsageStatsFallbackAt >= usageStatsFallbackGapMs) {
            lastUsageStatsFallbackAt = now
            try {
                val stats = usageStatsManager.queryUsageStats(
                    UsageStatsManager.INTERVAL_DAILY,
                    now - 1000 * 10,
                    now
                )
                if (stats != null && stats.isNotEmpty()) {
                    currentApp = stats.maxByOrNull { it.lastTimeUsed }?.packageName ?: ""
                }
            } catch (e: Exception) {
                Log.w("CameraBlocker", "queryUsageStats failed", e)
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

        // Do not block our own app while user is inside this process.
        if (CamShield.isAppInForeground()) {
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
            prepareOverlayView()

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
            if (cameraUnavailableAtMs > 0L) {
                val latencyMs = System.currentTimeMillis() - cameraUnavailableAtMs
                Log.d("CameraBlocker", "Overlay SHOWN (latency=${latencyMs}ms)")
            } else {
                Log.d("CameraBlocker", "Overlay SHOWN")
            }
        } catch (e: Exception) {
            Log.e("CameraBlocker", "Failed to show overlay", e)
        }
    }

    private fun prepareOverlayView() {
        if (overlayView != null) return

        val inflater = getSystemService(LAYOUT_INFLATER_SERVICE) as LayoutInflater
        overlayView = inflater.inflate(R.layout.activity_blocked, null)

        val btnDismiss = overlayView?.findViewById<Button>(R.id.btnDismiss)
        btnDismiss?.setOnClickListener {
            val startMain = Intent(Intent.ACTION_MAIN)
            startMain.addCategory(Intent.CATEGORY_HOME)
            startMain.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            startActivity(startMain)
        }
    }

    private fun hideOverlay() {
        if (!isOverlayShowing) return

        try {
            if (overlayView != null && windowManager != null) {
                windowManager?.removeView(overlayView)
            }
            isOverlayShowing = false
            cameraUnavailableAtMs = 0L
            Log.d("CameraBlocker", "Overlay HIDDEN")
        } catch (e: Exception) {
            Log.e("CameraBlocker", "Failed to hide overlay", e)
        }
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