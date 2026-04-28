package com.camshield.admin.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.camshield.admin.viewmodel.DeviceViewModel
import com.sierra.admin.auth.TokenManager
import com.sierra.admin.modal.ActiveDeviceItem
import com.sierra.admin.modal.ApiResult
import com.sierra.admin.modal.EnrollmentDetail
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

private val BgDark = Color(0xFF0B101F)
private val CardBg = Color(0xFF161C2C)
private val AccentBlue = Color(0xFF2196F3)
private val TextGray = Color(0xFF8A92A6)
private val StatusGreen = Color(0xFF4CAF50)
private val DangerRed = Color(0xFFEF5350)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterialApi::class)
@Composable
fun DeviceDetailScreen(
    deviceId: String,
    navController: NavHostController,
    viewModel: DeviceViewModel,
    tokenManager: TokenManager,
    onUnauthorized: () -> Unit
) {
    val enrollmentState by viewModel.enrollmentState.collectAsState()
    val selectedDevice by viewModel.selectedDevice.collectAsState()
    val selectedEnrollmentId = selectedDevice?.enrollmentId
        ?: selectedDevice?.lastEnrollment
        ?: ""

    val snackbarHostState = remember { SnackbarHostState() }
    val isRefreshing = enrollmentState is ApiResult.Loading && viewModel.enrollmentState.value != null
    val pullRefreshState = rememberPullRefreshState(
        refreshing = isRefreshing,
        onRefresh = { viewModel.loadEnrollment(deviceId, selectedEnrollmentId) }
    )

    LaunchedEffect(deviceId, selectedEnrollmentId) {
        viewModel.loadEnrollment(deviceId, selectedEnrollmentId)
    }

    LaunchedEffect(enrollmentState) {
        if (enrollmentState is ApiResult.Error && (enrollmentState as ApiResult.Error).code == 401) {
            onUnauthorized()
        }
    }

    Scaffold(
        containerColor = BgDark,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text("Device Detail", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                },
                navigationIcon = {
                    IconButton(onClick = {
                        viewModel.resetEnrollment()
                        viewModel.clearSelectedDevice()
                        navController.popBackStack()
                    }) {
                        Icon(Icons.Default.ArrowBack, null, tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = BgDark)
            )
        },
        snackbarHost = {
            SnackbarHost(snackbarHostState) { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = Color(0xFF2E7D32),
                    contentColor = Color.White
                )
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .pullRefresh(pullRefreshState)
        ) {
            when (val state = enrollmentState) {
                is ApiResult.Loading -> Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = AccentBlue)
                }

                is ApiResult.Error -> Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Text(
                            if (state.code == 404) {
                                "No active enrollment found for this device."
                            } else {
                                state.message.ifBlank { "Couldn't load device details. Pull to refresh or try again." }
                            },
                            color = DangerRed,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = { viewModel.loadEnrollment(deviceId, selectedEnrollmentId) },
                            colors = ButtonDefaults.buttonColors(containerColor = AccentBlue)
                        ) {
                            Text("Retry")
                        }
                    }
                }

                is ApiResult.Success -> EnrollmentDetailContent(
                    enrollment = state.data,
                    selectedDevice = selectedDevice,
                    padding = PaddingValues(0.dp)
                )

                else -> {}
            }

            PullRefreshIndicator(
                refreshing = isRefreshing,
                state = pullRefreshState,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 8.dp),
                contentColor = AccentBlue,
                backgroundColor = CardBg
            )
        }
    }
}

@Composable
private fun EnrollmentDetailContent(
    enrollment: EnrollmentDetail,
    selectedDevice: ActiveDeviceItem?,
    padding: PaddingValues
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        DetailCard(title = "Device Information") {
            DetailRow("Device Name", enrollment.device.deviceName)
            DetailRow("Model", enrollment.device.model)
            val osText = selectedDevice?.device?.osVersion?.takeIf { it.isNotBlank() } ?: ""
            DetailRow(
                "Platform",
                buildString {
                    append(enrollment.device.platform.uppercase())
                    if (osText.isNotBlank()) append(" • OS $osText")
                }
            )
            selectedDevice?.visitorId
                ?.takeIf { it.isNotBlank() }
                ?.let { DetailRow("Visitor ID", it) }
            DetailRow("Status", enrollment.device.status) { text ->
                val isActive = text.equals("active", ignoreCase = true)
                val isInactive = text.equals("inactive", ignoreCase = true)
                Text(
                    text = when {
                        isActive -> "ENTRY DONE"
                        isInactive -> "EXIT LOGGED"
                        else -> text.uppercase()
                    },
                    color = if (isActive) StatusGreen else DangerRed,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            val lastSeen = selectedDevice?.lastActivity ?: selectedDevice?.updatedAt ?: selectedDevice?.createdAt
            if (!lastSeen.isNullOrBlank()) {
                DetailRow("Last Active", formatDateTimeFriendly(lastSeen))
            }
        }

        DetailCard(title = "Currently At Facility") {
            DetailRow("Facility", enrollment.facility.name)
            enrollment.facility.facilityId?.let { DetailRow("Facility ID", it) }
            DetailRow("Status") {
                FacilityStatusChip(enrollment.facility.status)
            }
        }

        DetailCard(title = "Enrollment Details") {
            enrollment.entryQRCode?.let { qr ->
                DetailRow("Entry QR Code", qr.name.ifBlank { qr.id })
            }
            val enrolledAt = enrollment.enrolledAt.ifBlank { selectedDevice?.enrolledAt.orEmpty() }
            if (enrolledAt.isNotBlank()) {
                DetailRow("Entered At", formatDateTimeFriendly(enrolledAt))
            }

            val unenrolledAt = enrollment.unenrolledAt ?: selectedDevice?.unenrolledAt
            if (!unenrolledAt.isNullOrBlank()) {
                DetailRow("Exited At", formatDateTimeFriendly(unenrolledAt))
            }
        }
    }
}

@Composable
private fun FacilityStatusChip(status: String) {
    val isActive = status.equals("active", ignoreCase = true)
    Box(
        modifier = Modifier
            .background(
                color = if (isActive) StatusGreen.copy(alpha = 0.15f) else DangerRed.copy(alpha = 0.15f),
                shape = RoundedCornerShape(6.dp)
            )
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(
            text = status.uppercase(),
            color = if (isActive) StatusGreen else DangerRed,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun DetailCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                title,
                color = AccentBlue,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.5.sp
            )
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun DetailRow(
    label: String,
    value: String = "",
    smallText: Boolean = false,
    valueContent: (@Composable (String) -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(label, color = TextGray, fontSize = 13.sp, modifier = Modifier.weight(0.45f))
        if (valueContent != null) {
            Box(modifier = Modifier.weight(0.55f), contentAlignment = Alignment.TopStart) {
                valueContent(value)
            }
        } else {
            Text(
                value.ifBlank { "—" },
                color = Color.White,
                fontSize = if (smallText) 11.sp else 13.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(0.55f),
                textAlign = TextAlign.Start
            )
        }
    }
}

private val detailFriendlyFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("dd MMM yyyy, h:mm a")
private fun formatDateTimeFriendly(raw: String): String = runCatching {
    detailFriendlyFormatter.format(ZonedDateTime.ofInstant(Instant.parse(raw), ZoneId.systemDefault()))
}.getOrElse {
    runCatching {
        val cleaned = raw.removeSuffix("Z")
        detailFriendlyFormatter.format(LocalDateTime.parse(cleaned).atZone(ZoneId.systemDefault()))
    }.getOrElse {
        raw.replace("T", " ").take(19)
    }
}