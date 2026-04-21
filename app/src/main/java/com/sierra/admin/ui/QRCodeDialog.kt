package com.camshield.admin.ui

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.camshield.admin.viewmodel.FacilityViewModel
import qrcode.QRCode
import qrcode.color.Colors
import com.sierra.admin.modal.ApiResult
import com.sierra.admin.modal.FacilityData
import com.sierra.admin.modal.QRData
import com.sierra.admin.modal.QRPair
import com.sierra.admin.utils.DownloadNotificationManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val QrBgDark = Color(0xFF0B101F)
private val QrCardBg = Color(0xFF161C2C)
private val QrAccentBlue = Color(0xFF2196F3)
private val QrTextGray = Color(0xFF8A92A6)
private val QrStatusRed = Color(0xFFEF5350)
private val QrStatusGreen = Color(0xFF4CAF50)

enum class QRType { ENTRY, EXIT }

private const val TAG = "QRCodeDialog"

@Composable
fun QRCodeDialog(
    facility: FacilityData,
    viewModel: FacilityViewModel,
    focus: QRType? = null,
    onDismiss: () -> Unit,
    showSnackbar: (String) -> Unit
) {
    val qrState by viewModel.qrState.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pendingDownload by remember { mutableStateOf<PendingDownload?>(null) }
    val notificationManager = remember { DownloadNotificationManager(context) }
    
    val performSave: (Bitmap, String) -> Unit = { bitmap, filename ->
        scope.launch {
            notificationManager.showDownloadStartedNotification(filename)
            val success = withContext(Dispatchers.IO) { 
                saveQRToDownloads(context, bitmap, filename, notificationManager) 
            }
            if (success) {
                notificationManager.showDownloadSuccessNotification(filename)
                showSnackbar("\"$filename.png\" saved to Downloads")
            } else {
                notificationManager.showDownloadErrorNotification(filename)
                showSnackbar("Failed to save QR code")
            }
            onDismiss()
        }
    }

    val embeddedPair = remember(facility.activeQRCodes) {
        val entry = facility.activeQRCodes.firstOrNull { it.type.equals("entry", true) }
        val exit = facility.activeQRCodes.firstOrNull { it.type.equals("exit", true) }
        QRPair(entry = entry, exit = exit)
    }

    LaunchedEffect(facility.id, embeddedPair) {
        val hasEmbedded = embeddedPair.entry != null || embeddedPair.exit != null
        if (!hasEmbedded) viewModel.loadQRCodes(facility.id)
    }

    Dialog(
        onDismissRequest = {
            viewModel.resetQR()
            onDismiss()
        },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .wrapContentHeight(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = QrCardBg)
        ) {
            Column(
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 14.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Header
                Text(
                    "QR Codes",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    facility.name,
                    color = QrAccentBlue,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center
                )

                HorizontalDivider(color = Color(0xFF2A3245))

                val state = qrState
                val fallbackPair = if (embeddedPair.entry != null || embeddedPair.exit != null) embeddedPair else null
                when (state) {
                    is ApiResult.Loading -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = QrAccentBlue)
                        }
                    }

                    is ApiResult.Error -> {
                        if (fallbackPair == null) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.padding(vertical = 16.dp)
                            ) {
                                Text(
                                    state.message,
                                    color = QrStatusRed,
                                    textAlign = TextAlign.Center,
                                    fontSize = 14.sp
                                )
                                Button(
                                    onClick = { 
                                        viewModel.loadQRCodes(facility.id)
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = QrAccentBlue)
                                ) { Text("Retry") }
                            }
                        } else {
                            QRContent(
                                qrPair = fallbackPair,
                                focus = focus,
                                facility = facility,
                                showSnackbar = showSnackbar,
                                context = context,
                                scope = scope,
                                onDownloadConflict = { pendingDownload = it },
                                onSave = performSave
                            )
                        }
                    }

                    is ApiResult.Success -> {
                        QRContent(
                            qrPair = state.data,
                            focus = focus,
                            facility = facility,
                            showSnackbar = showSnackbar,
                            context = context,
                            scope = scope,
                            onDownloadConflict = { pendingDownload = it },
                            onSave = performSave
                        )
                    }

                    null -> {
                        fallbackPair?.let {
                            QRContent(
                                qrPair = it,
                                focus = focus,
                                facility = facility,
                                showSnackbar = showSnackbar,
                                context = context,
                                scope = scope,
                                onDownloadConflict = { pendingDownload = it },
                                onSave = performSave
                            )
                        }
                    }
                }

                OutlinedButton(
                    onClick = {
                        viewModel.resetQR()
                        onDismiss()
                    },
                    modifier = Modifier.align(Alignment.End),
                    border = ButtonDefaults.outlinedButtonBorder(enabled = true).copy(width = 1.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = QrTextGray)
                ) {
                    Text("Close")
                }
            }
        }
    }

    pendingDownload?.let { pending ->
        AlertDialog(
            onDismissRequest = { pendingDownload = null },
            modifier = Modifier.fillMaxWidth(1f),
            confirmButton = {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp)
                ) {
                    Button(
                        onClick = {
                            pendingDownload = null
                            val filename = "${pending.baseName}_copy"
                            performSave(pending.bitmap, filename)
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = QrAccentBlue),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(vertical = 10.dp)
                    ) { Text("Download Again", color = Color.White) }
                    OutlinedButton(
                        onClick = { pendingDownload = null },
                        modifier = Modifier.weight(1f),
                        border = ButtonDefaults.outlinedButtonBorder(enabled = true).copy(width = 1.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = QrTextGray),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(vertical = 10.dp)
                    ) { Text("Keep Existing") }
                }
            },
            dismissButton = {},
            title = { Text("Already downloaded", color = Color.White) },
            text = { Text("You already saved this QR. Do you want to download a fresh copy?", color = QrTextGray) },
            containerColor = QrCardBg,
            tonalElevation = 4.dp,
            shape = RoundedCornerShape(14.dp)
        )
    }
}

@Composable
private fun QRContent(
    qrPair: QRPair,
    focus: QRType?,
    facility: FacilityData,
    showSnackbar: (String) -> Unit,
    context: Context,
    scope: kotlinx.coroutines.CoroutineScope,
    onDownloadConflict: (PendingDownload) -> Unit,
    onSave: (Bitmap, String) -> Unit
) {
    // Permission launcher for notifications (Android 13+)
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            // Permission granted - proceed silently without snackbar
        } else {
            showSnackbar("Permission denied. Notifications won't be shown.")
        }
    }
    
    val requestedQrs = when (focus) {
        QRType.ENTRY -> listOfNotNull(qrPair.entry)
        QRType.EXIT -> listOfNotNull(qrPair.exit)
        null -> listOfNotNull(qrPair.entry, qrPair.exit)
    }

    if (requestedQrs.isEmpty()) {
        Text(
            "QR code not available for this selection.",
            color = QrStatusRed,
            textAlign = TextAlign.Center
        )
        return
    }

    val allBitmaps = remember { mutableStateMapOf<String, Bitmap>() }

    // Generate only the requested QR bitmaps
    LaunchedEffect(qrPair, focus) {
        requestedQrs.forEach { qr ->
            val content = qr.url
                .ifBlank { qr.value }
                .ifBlank { qr.token }
                .ifBlank { qr.qrCodeId ?: "" }
                .ifBlank { qr.id }
            if (content.isNotBlank()) {
                val bmp = withContext(Dispatchers.Default) {
                    generateQRBitmap(content)
                }
                if (bmp != null) allBitmaps[qr.id] = bmp
            }
        }
    }

    fun handleDownload(bitmap: Bitmap, qrData: QRData, isEntry: Boolean, force: Boolean = false) {
        // Request permission if needed (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
        
        val validDate = qrData.generatedForDate ?: qrData.validUntil ?: qrData.validFrom ?: qrData.qrCodeId ?: "qr"
        val dateStr = validDate.take(10).ifBlank { "qr" }
        val baseName = if (isEntry) "Entry_Code_$dateStr" else "Exit_Code_$dateStr"
        val filename = if (force) "${baseName}_${System.currentTimeMillis()}" else baseName
        val alreadyExists = !force && fileExists(context, "$baseName.png")
        if (alreadyExists) {
            onDownloadConflict(PendingDownload(bitmap, baseName, qrData, isEntry))
        } else {
            onSave(bitmap, filename)
        }
    }

    requestedQrs.forEachIndexed { index, qrData ->
        val isEntry = qrData.type.equals("entry", ignoreCase = true) ||
            qrData.id == qrPair.entry?.id ||
            qrData.qrCodeId == qrPair.entry?.qrCodeId
        val label = if (isEntry) "Entry QR Code" else "Exit QR Code"
        QRCodeSection(
            label = label,
            qrData = qrData,
            bitmap = allBitmaps[qrData.id],
            onDownload = { bmp -> handleDownload(bmp, qrData, isEntry) }
        )
        if (index < requestedQrs.lastIndex && focus == null) {
            HorizontalDivider(color = Color(0xFF2A3245))
        }
    }

    // Download Both button (only when showing both)
    val entryBmp = qrPair.entry?.id?.let { allBitmaps[it] }
    val exitBmp = qrPair.exit?.id?.let { allBitmaps[it] }
    if (focus == null && entryBmp != null && exitBmp != null) {
        Button(
            onClick = {
                handleDownload(entryBmp, qrPair.entry!!, true)
                handleDownload(exitBmp, qrPair.exit!!, false)
            },
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentWidth(Alignment.CenterHorizontally),
            colors = ButtonDefaults.buttonColors(containerColor = QrAccentBlue),
            shape = RoundedCornerShape(10.dp)
        ) {
            Icon(
                Icons.Default.Download,
                null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text("Download Both QR Codes", fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun QRCodeSection(
    label: String,
    qrData: QRData,
    bitmap: Bitmap?,
    onDownload: (Bitmap) -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            label,
            color = QrAccentBlue,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.5.sp
        )
        if (bitmap != null) {
            Box(
                modifier = Modifier
                    .size(200.dp)
                    .clip(RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = label,
                    modifier = Modifier.fillMaxSize()
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .size(200.dp)
                    .background(Color(0xFF0D1426), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    color = QrAccentBlue,
                    modifier = Modifier.size(32.dp),
                    strokeWidth = 2.dp
                )
            }
        }
        val validDate = (qrData.generatedForDate ?: qrData.validUntil ?: qrData.validFrom).orEmpty().take(10)
        val hasStatus = qrData.status.isNotBlank()
        if (validDate.isNotBlank() || hasStatus) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (validDate.isNotBlank()) {
                    Text("Valid: ${formatDateTimeFriendly2(validDate)}", color = QrTextGray, fontSize = 11.sp)
                }
                if (hasStatus) {
                    val isActive = qrData.status.equals("active", true)
                    Text(
                        "QR Status: ${qrData.status.replaceFirstChar { it.uppercase() }}",
                        color = if (isActive) QrStatusGreen else QrStatusRed,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
        if (bitmap != null) {
            OutlinedButton(
                onClick = { 
                    onDownload(bitmap)
                },
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = QrAccentBlue),
                border = androidx.compose.foundation.BorderStroke(1.dp, QrAccentBlue)
            ) {
                Icon(Icons.Default.Download, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Download", fontSize = 13.sp)
            }
        }
    }
}

private fun generateQRBitmap(content: String, size: Int = 512): Bitmap? {
    return try {
        val pngBytes = QRCode.ofSquares()
            .withColor(Colors.BLACK)
            .withBackgroundColor(Colors.WHITE)
            .withSize(16)
            .build(content)
            .render()
            .getBytes()
        val raw = BitmapFactory.decodeByteArray(pngBytes, 0, pngBytes.size) ?: return null
        if (raw.width == size && raw.height == size) {
            raw
        } else {
            Bitmap.createScaledBitmap(raw, size, size, false)
        }
    } catch (e: Exception) {
        null
    }
}

private fun saveQRToDownloads(context: Context, bitmap: Bitmap, filename: String, notificationManager: DownloadNotificationManager): Boolean {
    return try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, "$filename.png")
                put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
            val uri = context.contentResolver.insert(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                contentValues
            ) ?: return false
            context.contentResolver.openOutputStream(uri)?.use { out ->
                notificationManager.updateDownloadProgress(filename, 50)
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                notificationManager.updateDownloadProgress(filename, 100)
                Log.d(TAG, "Successfully saved QR code to Downloads: $filename.png")
            }
            true
        } else {
            @Suppress("DEPRECATION")
            val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            dir.mkdirs()
            val file = java.io.File(dir, "$filename.png")
            java.io.FileOutputStream(file).use { out ->
                notificationManager.updateDownloadProgress(filename, 50)
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                notificationManager.updateDownloadProgress(filename, 100)
            }
            true
        }
    } catch (e: Exception) {
        notificationManager.showDownloadErrorNotification(filename, "Error: ${e.message}")
        false
    }
}

private fun fileExists(context: Context, filenameWithExt: String): Boolean {
    return try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val projection = arrayOf(MediaStore.MediaColumns.DISPLAY_NAME)
            context.contentResolver.query(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                projection,
                "${MediaStore.MediaColumns.DISPLAY_NAME} = ?",
                arrayOf(filenameWithExt),
                null
            )?.use { cursor -> cursor.moveToFirst() }
                ?: false
        } else {
            @Suppress("DEPRECATION")
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), filenameWithExt).exists()
        }
    } catch (e: Exception) {
        false
    }
}

private data class PendingDownload(
    val bitmap: Bitmap,
    val baseName: String,
    val qrData: QRData,
    val isEntry: Boolean
)
private val dateOnlyFormatter = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.getDefault())
private fun formatDateTimeFriendly2(raw: String?): String {
    if (raw.isNullOrBlank()) return ""

    return runCatching {
        // 1. Try parsing as an Instant (ISO-8601 with Z)
        val date = ZonedDateTime.ofInstant(Instant.parse(raw), ZoneId.systemDefault())
        dateOnlyFormatter.format(date)
    }.getOrElse {
        runCatching {
            // 2. Try parsing as LocalDateTime (T separator, no Z)
            val cleaned = raw.removeSuffix("Z")
            val date = LocalDateTime.parse(cleaned)
            dateOnlyFormatter.format(date)
        }.getOrElse {
            runCatching {
                // 3. Try parsing as a simple LocalDate (yyyy-MM-dd)
                val date = java.time.LocalDate.parse(raw.take(10))
                dateOnlyFormatter.format(date)
            }.getOrElse {
                // 4. Fallback: Just take the first 10 chars (yyyy-MM-dd)
                raw.take(10)
            }
        }
    }
}
