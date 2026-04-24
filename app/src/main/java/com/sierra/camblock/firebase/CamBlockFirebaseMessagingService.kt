package com.sierra.camblock.firebase

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.sierra.camblock.CameraBlockerService
import com.sierra.camblock.R
import com.sierra.camblock.activity.SplashActivity
import com.sierra.camblock.api.RetrofitClient
import com.sierra.camblock.manager.DeviceAdminManager
import com.sierra.camblock.utils.DeviceUtils
import com.sierra.camblock.utils.PrefsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class CamBlockFirebaseMessagingService : FirebaseMessagingService() {

    companion object {
        private const val TAG = "FCMService"
        private const val CHANNEL_ID = "camshield_notifications"
        private const val CHANNEL_NAME = "CamShield Notifications"
        private const val ACTION_FORCE_EXIT_NOTIFICATION = "com.sierra.camblock.action.FORCE_EXIT_NOTIFICATION"
        private const val EXTRA_NOTIFICATION_DATA = "notification_data"
        private const val EXTRA_TYPE = "type"
        private const val TYPE_FORCE_EXIT_APPROVED = "FORCE_EXIT_APPROVED"
        private const val TYPE_RESTORE = "RESTORE"

        /**
         * Get the current FCM token. If not available in preferences,
         * it will be fetched from Firebase asynchronously.
         */
        suspend fun getOrFetchToken(context: Context): String? {
            val prefsManager = PrefsManager(context)
            val cachedToken = prefsManager.pushToken

            if (cachedToken != null) {
                Log.d(TAG, "Using cached FCM token")
                return cachedToken
            }

            Log.d(TAG, "No cached token, fetching from Firebase")
            return try {
                com.google.firebase.messaging.FirebaseMessaging.getInstance().token.await()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to fetch FCM token", e)
                null
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "FCM Token refreshed: $token")

        // Save the token to PrefsManager
        val prefsManager = PrefsManager(this)
        prefsManager.pushToken = token
        Log.d(TAG, "FCM Token $token")
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        Log.d(TAG, "Message received from: ${remoteMessage.from}")

        // Check if message contains data payload
        if (remoteMessage.data.isNotEmpty()) {
            Log.d(TAG, "Message data payload: ${remoteMessage.data}")

            val data = remoteMessage.data
            val type = data["type"] ?: return

            when (type) {
                TYPE_FORCE_EXIT_APPROVED, TYPE_RESTORE -> handleRestoreNotification(data)
                "REQUEST_REJECTED" -> handleRequestRejectedNotification(data)
                else -> Log.w(TAG, "Unknown notification type: $type")
            }
        }

        // Check if message contains notification payload
        remoteMessage.notification?.let {
            Log.d(TAG, "Message Notification Body: ${it.body}")
            showNotification(
                title = it.title ?: "CamShield",
                body = it.body ?: "New notification",
                data = remoteMessage.data
            )
        }
    }

    private fun handleRestoreNotification(data: Map<String, String>) {
        val deviceId = data["deviceId"] ?: return
        val restoreToken = data["token"] ?: return
        val currentDeviceId = DeviceUtils.getDeviceId(this)

        Log.d(TAG, "Handling restore notification for device: $deviceId")

        if (deviceId == currentDeviceId) {
            // Verify this message is for this device
            performRemoteUnlock(restoreToken)
        } else {
            Log.w(TAG, "Restore notification for different device. Expected: $currentDeviceId, Got: $deviceId")
        }
    }

    private fun handleRequestRejectedNotification(data: Map<String, String>) {
        val requestId = data["requestId"] ?: return
        val deviceId = data["deviceId"] ?: return
        val currentDeviceId = DeviceUtils.getDeviceId(this)

        if (deviceId == currentDeviceId) {
            // Show notification to user about rejection
            showNotification(
                title = "CamShield - Force Exit Update",
                body = "Your force exit request has been reviewed. Check app for details.",
                data = mapOf(
                    "type" to "REQUEST_REJECTED",
                    "requestId" to requestId
                )
            )
        }
    }

    private fun performRemoteUnlock(restoreToken: String) {
        Log.d(TAG, "Performing remote unlock with token: $restoreToken")

    }

    private fun performLocalUnlock() {

    }

    private fun updateFcmTokenOnServer(token: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Implementation depends on your backend API
                // This would be an endpoint to update the FCM token for an enrolled device
                Log.d(TAG, "Updating FCM token on server: $token")
                // TODO: Implement token update API call
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update FCM token on server", e)
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for CamShield force exit requests and status updates"
                enableLights(true)
                enableVibration(true)
            }

            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun showNotification(title: String, body: String, data: Map<String, String>) {
        val intent = Intent(this, SplashActivity::class.java).apply {
            action = ACTION_FORCE_EXIT_NOTIFICATION
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_NOTIFICATION_DATA, HashMap(data))
            data[EXTRA_TYPE]?.let { putExtra(EXTRA_TYPE, it) }
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            System.currentTimeMillis().toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notificationBuilder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_logo)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(System.currentTimeMillis().toInt(), notificationBuilder.build())
    }
}
