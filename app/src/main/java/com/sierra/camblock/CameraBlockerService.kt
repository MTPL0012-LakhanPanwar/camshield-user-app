package com.sierra.camblock

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.AlarmManager
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
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.Button
import androidx.core.app.NotificationCompat
import com.sierra.camblock.activity.CameraDisabledActivity
import com.sierra.camblock.utils.PrefsManager

class CameraBlockerService : Service() {

    companion object {
        private const val TAG = "CameraBlocker"
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
    private var overlayLayoutParams: WindowManager.LayoutParams? = null
    private var isOverlayAttached = false
    private var isOverlayShowing = false

    // State tracking
    private var isCameraInUse = false // From Callback
    private var isCameraAppForeground = false // From Usage Stats
    private var lastForegroundCheckAt = 0L
    private var lastUsageStatsFallbackAt = 0L
    private var cameraUnavailableAtMs = 0L
    private var lastSettingsInterceptionAtMs = 0L
    private var isSettingsInterceptionInProgress = false

    private val settingsInterceptionCooldownMs = 1800L

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

    private val SETTINGS_PACKAGES = setOf(
        "com.android.settings",
        "com.android.permissioncontroller",
        "com.google.android.permissioncontroller",
        "com.oneplus.security",
        "com.oneplus.securitychain",
        "com.samsung.android.app.settings",
        "com.sec.android.app.settings",
        "com.miui.securitycenter",
        "com.miui.permcenter",
        "com.oppo.safe",
        "com.coloros.safecenter",
        "com.coloros.securitypermission",
        "com.oplus.safecenter",
        "com.oplus.securitypermission",
        "com.oplus.permissionmanager",
        "com.vivo.permissionmanager",
        "com.realme.securitycheck",
        "com.realme.securepay",
        "com.huawei.systemmanager"
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
            Log.d(TAG, "Camera Unavailable (In Use)")

            // Fast path: on Android 16+, usage stats foreground detection can lag.
            // Show blocker immediately when camera becomes unavailable and app is backgrounded.
            if (prefsManager.isLocked && !CamShield.isAppInForeground()) {
                showOverlay("camera_callback_fast_path")
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

        // Promote to foreground as early as possible to satisfy the Android O+
        // foreground-start time window before any potentially expensive setup.
        startForegroundService()

        // Register Camera Callback
        try {
            cameraManager.registerAvailabilityCallback(cameraCallback, handler)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register camera callback", e)
        }

        prepareOverlayView()
        attachOverlayIfNeeded()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!isRunning) {
            isRunning = true
            isServiceRunning = true
            handler.post(runnable)
        }
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)

        // MIUI/Redmi "clear all" may remove the task and kill the process.
        // If lock is still active, request a quick service restart to preserve blocking.
        if (!prefsManager.isLocked) {
            return
        }

        try {
            val restartIntent = Intent(applicationContext, CameraBlockerService::class.java)
            val flags = PendingIntent.FLAG_ONE_SHOT or
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
            val pendingIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                PendingIntent.getForegroundService(
                    applicationContext,
                    2001,
                    restartIntent,
                    flags
                )
            } else {
                PendingIntent.getService(
                    applicationContext,
                    2001,
                    restartIntent,
                    flags
                )
            }

            val alarmManager = getSystemService(ALARM_SERVICE) as AlarmManager
            val triggerAt = SystemClock.elapsedRealtime() + 800L
            try {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    triggerAt,
                    pendingIntent
                )
            } catch (se: SecurityException) {
                // Android 12+ may restrict exact alarms; fallback keeps recovery alive.
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    triggerAt,
                    pendingIntent
                )
                Log.w(TAG, "Exact alarm denied; using inexact restart", se)
            }
            Log.w(TAG, "Task removed while locked; scheduled blocker restart")
        } catch (e: Exception) {
            Log.e(TAG, "Failed scheduling restart on task removal", e)
        }
    }

    override fun onDestroy() {
        isRunning = false
        isServiceRunning = false
        handler.removeCallbacks(runnable)
        hideOverlay() // Ensure overlay is removed

        try {
            if (isOverlayAttached && overlayView != null && windowManager != null) {
                windowManager?.removeViewImmediate(overlayView)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fully detach overlay", e)
        }
        isOverlayAttached = false
        overlayLayoutParams = null

        try {
            cameraManager.unregisterAvailabilityCallback(cameraCallback)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to unregister camera callback", e)
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
            Log.e(TAG, "startForeground failed", e)
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
        var lastEventTimestamp = 0L
        var detectionSource = "none"
        var eventAgeMs = -1L

        val queryEventsStart = SystemClock.elapsedRealtime()
        var queryEventsCostMs = 0L
        var queryUsageStatsCostMs = -1L

        // Strategy 1: Usage Events (Precise)
        try {
            val events = usageStatsManager.queryEvents(now - 1000, now)
            val event = UsageEvents.Event()

            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                val isForegroundTransition =
                    event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND ||
                            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                                    event.eventType == UsageEvents.Event.ACTIVITY_RESUMED)
                if (isForegroundTransition) {
                    currentApp = event.packageName
                    lastEventTimestamp = event.timeStamp
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "queryEvents failed", e)
        }
        queryEventsCostMs = SystemClock.elapsedRealtime() - queryEventsStart
        if (currentApp.isNotEmpty() && lastEventTimestamp > 0L) {
            detectionSource = "queryEvents"
            eventAgeMs = now - lastEventTimestamp
        }

        // Strategy 2: Usage Stats Snapshot (Fallback)
        if (currentApp.isEmpty() && now - lastUsageStatsFallbackAt >= usageStatsFallbackGapMs) {
            lastUsageStatsFallbackAt = now
            val queryUsageStatsStart = SystemClock.elapsedRealtime()
            try {
                val stats = usageStatsManager.queryUsageStats(
                    UsageStatsManager.INTERVAL_DAILY,
                    now - 1000 * 10,
                    now
                )
                if (stats != null && stats.isNotEmpty()) {
                    val latest = stats.maxByOrNull { it.lastTimeUsed }
                    currentApp = latest?.packageName ?: ""
                    if (latest != null) {
                        detectionSource = "queryUsageStats"
                        eventAgeMs = now - latest.lastTimeUsed
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "queryUsageStats failed", e)
            }
            queryUsageStatsCostMs = SystemClock.elapsedRealtime() - queryUsageStatsStart
        }

        if (currentApp.isNotEmpty()) {
            val hasFreshForegroundSignal =
                detectionSource == "queryEvents" ||
                        (eventAgeMs >= 0 && eventAgeMs <= 2000L)
            if (hasFreshForegroundSignal) {
                detectAndWarnSettingsAccess(currentApp)
            }

            // Update state
            val wasForeground = isCameraAppForeground
            isCameraAppForeground = isCameraApp(currentApp)

            if (wasForeground != isCameraAppForeground) {
                Log.d(
                    TAG,
                    "Foreground App Changed: $currentApp (IsCameraApp: $isCameraAppForeground, source=$detectionSource, eventAge=${eventAgeMs}ms, queryEvents=${queryEventsCostMs}ms, queryStats=${queryUsageStatsCostMs}ms)"
                )
            }
        }
    }

    private fun detectAndWarnSettingsAccess(packageName: String) {
        if (!prefsManager.isLocked) return
        if (!isSettingsAppInForeground(packageName)) return
        if (CamShield.isAppInForeground()) return

        val now = SystemClock.elapsedRealtime()
        if (isSettingsInterceptionInProgress ||
            now - lastSettingsInterceptionAtMs < settingsInterceptionCooldownMs
        ) {
            return
        }

        isSettingsInterceptionInProgress = true
        lastSettingsInterceptionAtMs = now
        Log.w(TAG, "Blocked settings access while locked: $packageName")

        try {
            val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(homeIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to move to Home while intercepting settings", e)
        }

        handler.postDelayed({
            try {
                val disableIntent = Intent(this, CameraDisabledActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_CLEAR_TOP or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP
                    putExtra(CameraDisabledActivity.EXTRA_SHOW_TOAST, true)
                }
                startActivity(disableIntent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to relaunch CameraDisabledActivity after settings interception", e)
            } finally {
                // Keep a short hold before resetting, preventing rapid relaunch loops.
                handler.postDelayed({
                    isSettingsInterceptionInProgress = false
                }, 800L)
            }
        }, 300L)
    }

    private fun isSettingsAppInForeground(packageName: String): Boolean {
        if (packageName.isEmpty()) return false
        if (packageName == applicationContext.packageName) return false

        if (SETTINGS_PACKAGES.contains(packageName)) return true

        if (packageName.contains("settings", ignoreCase = true)) {
            return true
        }

        return packageName.contains("permission", ignoreCase = true) &&
                (packageName.contains("manager", ignoreCase = true) ||
                        packageName.contains("center", ignoreCase = true) ||
                        packageName.contains("controller", ignoreCase = true))
    }

    private fun updateOverlayState() {
        if (!prefsManager.isLocked) {
            if (isOverlayShowing) {
                hideOverlay("not_locked")
            }
            return
        }

        // Do not block our own app while user is inside this process.
        if (CamShield.isAppInForeground()) {
            if (isOverlayShowing) {
                hideOverlay("own_app_foreground")
            }
            return
        }

        if (isCameraInUse || isCameraAppForeground) {
            if (!isOverlayShowing) {
                showOverlay("state_eval cameraInUse=$isCameraInUse cameraForeground=$isCameraAppForeground")
            }
        } else {
            if (isOverlayShowing) {
                hideOverlay("camera_not_in_use")
            }
        }
    }

    private fun showOverlay(reason: String) {
        if (isOverlayShowing) {
            return
        }

        // Double check Overlay Permission
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            Log.e(TAG, "Cannot show overlay: Permission missing")
            return
        }

        try {
            prepareOverlayView()

            if (!isOverlayAttached) {
                attachOverlayIfNeeded()
            }

            val view = overlayView ?: return
            val params = overlayLayoutParams ?: createOverlayLayoutParams(interactive = true).also {
                overlayLayoutParams = it
            }

            params.flags = buildOverlayFlags(interactive = true)
            params.alpha = 1f
            view.visibility = View.VISIBLE
            view.alpha = 1f
            view.invalidate()

            val wmStart = SystemClock.elapsedRealtime()
            if (isOverlayAttached) {
                windowManager?.updateViewLayout(view, params)
            } else {
                windowManager?.addView(view, params)
                isOverlayAttached = true
            }
            val wmCostMs = SystemClock.elapsedRealtime() - wmStart

            isOverlayShowing = true

            if (cameraUnavailableAtMs > 0L) {
                val latencyMs = System.currentTimeMillis() - cameraUnavailableAtMs
                Log.d(TAG, "Overlay SHOWN (latency=${latencyMs}ms, wm=${wmCostMs}ms, reason=$reason)")
            } else {
                Log.d(TAG, "Overlay SHOWN (wm=${wmCostMs}ms, reason=$reason)")
            }
        } catch (e: Exception) {
            isOverlayAttached = false
            Log.e(TAG, "Failed to show overlay", e)
        }
    }

    private fun prepareOverlayView() {
        if (overlayView != null) return

        val inflater = getSystemService(LAYOUT_INFLATER_SERVICE) as LayoutInflater
        overlayView = inflater.inflate(R.layout.activity_blocked, null)
        overlayView?.isClickable = true
        overlayView?.isFocusable = true
        overlayView?.isFocusableInTouchMode = true

        val btnDismiss = overlayView?.findViewById<Button>(R.id.btnDismiss)
        btnDismiss?.setOnClickListener {
            val startMain = Intent(Intent.ACTION_MAIN)
            startMain.addCategory(Intent.CATEGORY_HOME)
            startMain.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            startActivity(startMain)
        }
    }

    private fun attachOverlayIfNeeded() {
        if (isOverlayAttached) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            Log.e(TAG, "Cannot pre-attach overlay: Permission missing")
            return
        }

        val view = overlayView ?: return

        try {
            val params = createOverlayLayoutParams(interactive = false)
            overlayLayoutParams = params
            view.visibility = View.VISIBLE
            view.alpha = 0f

            val wmStart = SystemClock.elapsedRealtime()
            windowManager?.addView(view, params)
            val wmCostMs = SystemClock.elapsedRealtime() - wmStart

            isOverlayAttached = true
            Log.d(TAG, "Overlay pre-attached hidden (wm=${wmCostMs}ms)")
        } catch (e: Exception) {
            isOverlayAttached = false
            Log.e(TAG, "Failed to pre-attach overlay", e)
        }
    }

    private fun createOverlayLayoutParams(interactive: Boolean): WindowManager.LayoutParams {
        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            buildOverlayFlags(interactive),
            PixelFormat.TRANSLUCENT
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            layoutParams.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Fill the full display bounds including system bar areas.
            layoutParams.setFitInsetsTypes(0)
            layoutParams.setFitInsetsSides(0)
            layoutParams.setFitInsetsIgnoringVisibility(true)
        }

        layoutParams.gravity = Gravity.TOP or Gravity.START
        // Keep hidden pre-attached overlays fully transparent at the window level.
        // On some Android 12 builds, view alpha alone is not enough for touch pass-through.
        layoutParams.alpha = if (interactive) 1f else 0f
        return layoutParams
    }

    private fun buildOverlayFlags(interactive: Boolean): Int {
        var flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED

        if (!interactive) {
            flags = flags or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        }

        return flags
    }

    private fun hideOverlay() {
        hideOverlay("unknown")
    }

    private fun hideOverlay(reason: String) {
        if (!isOverlayShowing) return

        try {
            val view = overlayView
            val params = overlayLayoutParams
            if (view != null && params != null && windowManager != null && isOverlayAttached) {
                params.flags = buildOverlayFlags(interactive = false)
                params.alpha = 0f
                view.visibility = View.VISIBLE
                view.alpha = 0f
                windowManager?.updateViewLayout(view, params)
            }
            isOverlayShowing = false
            cameraUnavailableAtMs = 0L
            Log.d(TAG, "Overlay HIDDEN (reason=$reason)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to hide overlay", e)
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