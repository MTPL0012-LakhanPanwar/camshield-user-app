package com.sierra.admin.utils

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.ui.geometry.RoundRect
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.sierra.camblock.R
import java.io.File

class DownloadNotificationManager(private val context: Context) {
    companion object {
        private const val TAG = "DownloadNotificationManager"
        private const val CHANNEL_ID = "qr_download_channel"
        private const val CHANNEL_NAME = "QR Code Downloads"
        private const val DOWNLOAD_NOTIFICATION_ID = 100
        private const val SUCCESS_NOTIFICATION_ID = 101
    }

    private val notificationManager: NotificationManager by lazy {
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, importance).apply {
                description = "Notifications for QR code downloads"
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        } else {
            true // Not required on older versions
        }
    }

    fun showDownloadStartedNotification(fileName: String) {
        if (!hasNotificationPermission()) {
            return
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("Downloading QR Code")
            .setContentText("Saving $fileName...")
            .setSmallIcon(R.drawable.outline_download_24)
            .setProgress(100, 0, true) // Indeterminate progress
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        notificationManager.notify(DOWNLOAD_NOTIFICATION_ID, notification)
    }

    fun updateDownloadProgress(fileName: String, progress: Int) {
        if (!hasNotificationPermission()) {
            return
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("Downloading QR Code")
            .setContentText("Saving $fileName... $progress%")
            .setSmallIcon(R.drawable.outline_download_24)
            .setProgress(100, progress, false)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        notificationManager.notify(DOWNLOAD_NOTIFICATION_ID, notification)
    }

    fun showDownloadSuccessNotification(fileName: String) {
        if (!hasNotificationPermission()) {
            return
        }

        // Cancel the progress notification
        cancelDownloadNotification()

        // Create intent to open the downloaded file
        val fileUri = getDownloadedFileUri(fileName)
        val openFileIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(fileUri, "image/png")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            SUCCESS_NOTIFICATION_ID,
            openFileIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("QR Code Downloaded")
            .setContentText("\"$fileName.png\" saved to Downloads")
            .setSmallIcon(R.drawable.outline_download_24)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)  // Open file when notification clicked
            .addAction(
                R.drawable.outline_download_24,
                "Open",
                pendingIntent
            )
            .build()

        notificationManager.notify(SUCCESS_NOTIFICATION_ID, notification)
    }

    fun showDownloadErrorNotification(fileName: String, errorMessage: String = "Failed to save QR code") {
        if (!hasNotificationPermission()) {
            return
        }

        cancelDownloadNotification()

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("Download Failed")
            .setContentText(errorMessage)
            .setSmallIcon(R.drawable.outline_download_24)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        notificationManager.notify(SUCCESS_NOTIFICATION_ID, notification)
    }

    private fun getDownloadedFileUri(fileName: String): Uri {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // For Android 10+, files are in MediaStore Downloads
            getUriFromMediaStore(fileName)
        } else {
            // For older versions, files are in external storage
            @Suppress("DEPRECATION")
            val file = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "$fileName.png")
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
        }
    }

    private fun getUriFromMediaStore(fileName: String): Uri {
        val projection = arrayOf(MediaStore.Downloads._ID)
        val selection = "${MediaStore.MediaColumns.DISPLAY_NAME} = ?"
        val selectionArgs = arrayOf("$fileName.png")

        val cursor = context.contentResolver.query(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            null
        )

        var uri: Uri = Uri.EMPTY
        cursor?.use {
            if (it.moveToFirst()) {
                val id = it.getLong(it.getColumnIndexOrThrow(MediaStore.Downloads._ID))
                uri = Uri.withAppendedPath(MediaStore.Downloads.EXTERNAL_CONTENT_URI, id.toString())
            }
        }
        return uri
    }

    fun cancelDownloadNotification() {
        notificationManager.cancel(DOWNLOAD_NOTIFICATION_ID)
    }

}
