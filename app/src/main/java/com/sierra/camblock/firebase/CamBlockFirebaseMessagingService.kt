//package com.sierra.camblock.firebase
//
//import android.app.NotificationChannel
//import android.app.NotificationManager
//import android.app.PendingIntent
//import android.content.Context
//import android.content.Intent
//import android.os.Build
//import android.util.Log
//import androidx.core.app.NotificationCompat
//import com.google.firebase.messaging.FirebaseMessagingService
//import com.google.firebase.messaging.RemoteMessage
//import com.sierra.camblock.CameraBlockerService
//import com.sierra.camblock.R
//import com.sierra.camblock.activity.PermissionRestoreActivity
//import com.sierra.camblock.api.RetrofitClient
//import com.sierra.camblock.api.models.RestoreFromPushRequest
//import com.sierra.camblock.manager.DeviceAdminManager
//import com.sierra.camblock.utils.DeviceUtils
//import com.sierra.camblock.utils.PrefsManager
//import kotlinx.coroutines.CoroutineScope
//import kotlinx.coroutines.Dispatchers
//import kotlinx.coroutines.launch
//
//class CamBlockFirebaseMessagingService : FirebaseMessagingService() {
//
//    companion object {
//        private const val TAG = "FCMService"
//        private const val CHANNEL_ID = "camshield_notifications"
//        private const val CHANNEL_NAME = "CamShield Notifications"
//    }
//
//    override fun onCreate() {
//        super.onCreate()
//        createNotificationChannel()
//    }
//
//    override fun onNewToken(token: String) {
//        super.onNewToken(token)
//        Log.d(TAG, "FCM Token refreshed: $token")
//
//        // Save token locally
//        PrefsManager(this).fcmToken = token
//
//        // If device is currently enrolled, update token on server
//        if (PrefsManager(this).isEnrolled) {
//            updateFcmTokenOnServer(token)
//        }
//    }
//
//    override fun onMessageReceived(remoteMessage: RemoteMessage) {
//        super.onMessageReceived(remoteMessage)
//
//        Log.d(TAG, "Message received from: ${remoteMessage.from}")
//
//        // Check if message contains data payload
//        remoteMessage.data.isNotEmpty().let {
//            Log.d(TAG, "Message data payload: ${remoteMessage.data}")
//
//            val data = remoteMessage.data
//            val type = data["type"] ?: return
//
//            when (type) {
//                "RESTORE" -> handleRestoreNotification(data)
//                "REQUEST_REJECTED" -> handleRequestRejectedNotification(data)
//                else -> Log.w(TAG, "Unknown notification type: $type")
//            }
//        }
//
//        // Check if message contains notification payload
//        remoteMessage.notification?.let {
//            Log.d(TAG, "Message Notification Body: ${it.body}")
//            showNotification(
//                title = it.title ?: "CamShield",
//                body = it.body ?: "New notification",
//                data = remoteMessage.data
//            )
//        }
//    }
//
//    private fun handleRestoreNotification(data: Map<String, String>) {
//        val deviceId = data["deviceId"] ?: return
//        val restoreToken = data["token"] ?: return
//        val currentDeviceId = DeviceUtils.getDeviceId(this)
//
//        Log.d(TAG, "Handling restore notification for device: $deviceId")
//
//        if (deviceId == currentDeviceId) {
//            // Verify this message is for this device
//            performRemoteUnlock(restoreToken)
//        } else {
//            Log.w(TAG, "Restore notification for different device. Expected: $currentDeviceId, Got: $deviceId")
//        }
//    }
//
//    private fun handleRequestRejectedNotification(data: Map<String, String>) {
//        val requestId = data["requestId"] ?: return
//        val deviceId = data["deviceId"] ?: return
//        val currentDeviceId = DeviceUtils.getDeviceId(this)
//
//        if (deviceId == currentDeviceId) {
//            // Show notification to user about rejection
//            showNotification(
//                title = "CamShield - Force Exit Update",
//                body = "Your force exit request has been reviewed. Check app for details.",
//                data = mapOf(
//                    "type" to "REQUEST_REJECTED",
//                    "requestId" to requestId
//                )
//            )
//        }
//    }
//
//    private fun performRemoteUnlock(restoreToken: String) {
//        Log.d(TAG, "Performing remote unlock with token: $restoreToken")
//
//        CoroutineScope(Dispatchers.IO).launch {
//            try {
//                // Call restore API
//                val response = RetrofitClient.apiService.restoreFromPush(
//                    RestoreFromPushRequest(
//                        token = restoreToken,
//                        deviceId = DeviceUtils.getDeviceId(this@CamBlockFirebaseMessagingService)
//                    )
//                )
//
//                if (response.isSuccessful && response.body()?.status == "success") {
//                    // Perform local unlock
//                    performLocalUnlock()
//
//                    // Show success notification
//                    showNotification(
//                        title = "CamShield - Force Exit Approved",
//                        body = "Your permissions have been restored successfully.",
//                        data = mapOf("type" to "RESTORE_SUCCESS")
//                    )
//                } else {
//                    Log.e(TAG, "Restore API failed: ${response.body()?.message}")
//                    showNotification(
//                        title = "CamShield - Error",
//                        body = "Failed to restore permissions. Please try again.",
//                        data = mapOf("type" to "RESTORE_ERROR")
//                    )
//                }
//            } catch (e: Exception) {
//                Log.e(TAG, "Error during remote unlock", e)
//                showNotification(
//                    title = "CamShield - Error",
//                    body = "Network error. Please check connection and try again.",
//                    data = mapOf("type" to "RESTORE_ERROR")
//                )
//            }
//        }
//    }
//
//    private fun performLocalUnlock() {
//        try {
//            // Handle Xiaomi specific lock task
//            if (DeviceUtils.isTargetedXiaomiVersion()) {
//                stopLockTask()
//            }
//
//            // Update preferences
//            PrefsManager(this).isLocked = false
//            PrefsManager(this).activeVisitorId = ""
//            PrefsManager(this).isEnrolled = false
//
//            // Stop camera blocker service
//            stopService(Intent(this, CameraBlockerService::class.java))
//
//            // Remove device admin
//            val deviceAdminManager = DeviceAdminManager(this)
//            if (deviceAdminManager.unlockCamera()) {
//                deviceAdminManager.removeDeviceAdmin()
//            }
//
//            // Navigate to restore activity
//            val intent = Intent(this, PermissionRestoreActivity::class.java).apply {
//                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
//                putExtra("from_push", true)
//            }
//            startActivity(intent)
//
//            Log.d(TAG, "Local unlock completed successfully")
//        } catch (e: Exception) {
//            Log.e(TAG, "Error during local unlock", e)
//        }
//    }
//
//    private fun updateFcmTokenOnServer(token: String) {
//        CoroutineScope(Dispatchers.IO).launch {
//            try {
//                // Implementation depends on your backend API
//                // This would be an endpoint to update the FCM token for an enrolled device
//                Log.d(TAG, "Updating FCM token on server: $token")
//                // TODO: Implement token update API call
//            } catch (e: Exception) {
//                Log.e(TAG, "Failed to update FCM token on server", e)
//            }
//        }
//    }
//
//    private fun createNotificationChannel() {
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
//            val channel = NotificationChannel(
//                CHANNEL_ID,
//                CHANNEL_NAME,
//                NotificationManager.IMPORTANCE_HIGH
//            ).apply {
//                description = "Notifications for CamShield force exit requests and status updates"
//                enableLights(true)
//                enableVibration(true)
//            }
//
//            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
//            notificationManager.createNotificationChannel(channel)
//        }
//    }
//
//    private fun showNotification(title: String, body: String, data: Map<String, String>) {
//        val intent = Intent(this, PermissionRestoreActivity::class.java).apply {
//            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
//            putExtra("notification_data", HashMap(data))
//        }
//
//        val pendingIntent = PendingIntent.getActivity(
//            this,
//            0,
//            intent,
//            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
//        )
//
//        val notificationBuilder = NotificationCompat.Builder(this, CHANNEL_ID)
//            .setSmallIcon(R.mipmap.ic_launcher)
//            .setContentTitle(title)
//            .setContentText(body)
//            .setAutoCancel(true)
//            .setPriority(NotificationCompat.PRIORITY_HIGH)
//            .setContentIntent(pendingIntent)
//
//        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
//        notificationManager.notify(System.currentTimeMillis().toInt(), notificationBuilder.build())
//    }
//}
