package com.camshield.admin.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.camshield.admin.viewmodel.DeviceViewModel
import com.sierra.admin.auth.TokenManager
import com.sierra.admin.modal.ActiveDeviceItem
import com.sierra.admin.modal.ApiResult
import com.sierra.admin.modal.EnrollmentDetail
import kotlinx.coroutines.launch
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
    val forceExitState by viewModel.forceExitState.collectAsState()
    val selectedDevice by viewModel.selectedDevice.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showForceExitDialog by remember { mutableStateOf(false) }
    val adminUsername = tokenManager.getAdminUsername() ?: "Admin"
    val isForceExitLoading = forceExitState is ApiResult.Loading
    val isRefreshing = enrollmentState is ApiResult.Loading && viewModel.enrollmentState.value != null
    val pullRefreshState = rememberPullRefreshState(
        refreshing = isRefreshing,
        onRefresh = { viewModel.loadEnrollment(deviceId) }
    )

    LaunchedEffect(deviceId) {
        viewModel.loadEnrollment(deviceId)
    }

    LaunchedEffect(enrollmentState) {
        if (enrollmentState is ApiResult.Error && (enrollmentState as ApiResult.Error).code == 401) {
            onUnauthorized()
        }
    }

    LaunchedEffect(forceExitState) {
        when (val state = forceExitState) {
            is ApiResult.Success -> {
                val pushed = if (state.data.pushSent) "Push notification sent." else "Push not sent."
                scope.launch {
                    snackbarHostState.showSnackbar("Camera unlocked! $pushed")
                }
                viewModel.resetForceExit()
                viewModel.refreshDevices()
                viewModel.clearSelectedDevice()
                navController.popBackStack()
            }
            is ApiResult.Error -> {
                scope.launch { snackbarHostState.showSnackbar(state.message) }
                viewModel.resetForceExit()
            }
            else -> {}
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
                Snackbar(snackbarData = data, containerColor = Color(0xFF2E7D32), contentColor = Color.White)
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
                ) { CircularProgressIndicator(color = AccentBlue) }

                is ApiResult.Error -> Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                        Text(
                            if (state.code == 404) "No active enrollment found for this device." else state.message.ifBlank { "Couldn’t load device details. Pull to refresh or try again." },
                            color = DangerRed,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = { viewModel.loadEnrollment(deviceId) },
                            colors = ButtonDefaults.buttonColors(containerColor = AccentBlue)) {
                            Text("Retry")
                        }
                    }
                }

                is ApiResult.Success -> EnrollmentDetailContent(
                    enrollment = state.data,
                    selectedDevice = selectedDevice,
                    padding = PaddingValues(0.dp),
                    isForceExitLoading = isForceExitLoading,
                    onForceExitClick = { showForceExitDialog = true }
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

    if (showForceExitDialog) {
        ForceExitDialog(
            adminUsername = adminUsername,
            isLoading = isForceExitLoading,
            onDismiss = { showForceExitDialog = false },
            onConfirm = { reason ->
                showForceExitDialog = false
                viewModel.forceExit(deviceId, reason, adminUsername)
            }
        )
    }
}

@Composable
private fun EnrollmentDetailContent(
    enrollment: EnrollmentDetail,
    selectedDevice: ActiveDeviceItem?,
    padding: PaddingValues,
    isForceExitLoading: Boolean,
    onForceExitClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Device Info Card
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
                Text(
                    text = text.uppercase(),
                    color = if (text.lowercase() == "active") StatusGreen else DangerRed,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            val lastSeen = selectedDevice?.lastActivity ?: selectedDevice?.updatedAt ?: selectedDevice?.createdAt
            if (!lastSeen.isNullOrBlank()) {
                DetailRow("Last Active", formatDateTimeFriendly(lastSeen))
            }
        }

        // Facility Info Card
        DetailCard(title = "Currently At Facility") {
            DetailRow("Facility", enrollment.facility.name)
            enrollment.facility.facilityId?.let { DetailRow("Facility ID", it) }
            DetailRow("Status") {
                FacilityStatusChip(enrollment.facility.status)
            }
        }

        // Enrollment Info Card
        DetailCard(title = "Enrollment Details") {
            enrollment.entryQRCode?.let { qr ->
                DetailRow("Entry QR Code", qr.name.ifBlank { qr.id })
            }
            if (enrollment.enrolledAt.isNotBlank()) {
                DetailRow("Enrolled At", formatDateTimeFriendly(enrollment.enrolledAt))
            }
        }

        // Force Exit Button
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = onForceExitClick,
            modifier = Modifier
                .fillMaxWidth(0.75f)
                .align(Alignment.CenterHorizontally)
                .height(56.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = DangerRed),
            enabled = !isForceExitLoading
        ) {
            if (isForceExitLoading) {
                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                Icon(Icons.Default.ExitToApp, null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Force Exit & Unlock Camera", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
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
            Text(title, color = AccentBlue, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.5.sp)
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
                textAlign = androidx.compose.ui.text.style.TextAlign.Start
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

@Composable
private fun ForceExitDialog(
    adminUsername: String,
    isLoading: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var reason by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = { if (!isLoading) onDismiss() },
        containerColor = CardBg,
        title = {
            Text("Force Exit Device", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "This will unlock the device's camera and mark the visitor as exited.",
                    color = TextGray,
                    fontSize = 14.sp
                )
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Reason (optional)", color = TextGray, fontSize = 13.sp)
                    OutlinedTextField(
                        value = reason,
                        onValueChange = { reason = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("e.g. User left without checkout", color = Color(0xFF4A5568)) },
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        shape = RoundedCornerShape(8.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedContainerColor = Color(0xFF0D1426),
                            unfocusedContainerColor = Color(0xFF0D1426),
                            focusedBorderColor = DangerRed,
                            unfocusedBorderColor = Color(0xFF2A3245)
                        )
                    )
                }
                Text("Initiated by: $adminUsername", color = TextGray, fontSize = 12.sp)
            }
        },
        // Custom button row to center-align actions
        confirmButton = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 1.dp),
                horizontalArrangement = Arrangement.spacedBy(15.dp, Alignment.CenterHorizontally)
            ) {
                OutlinedButton(
                    onClick = { if (!isLoading) onDismiss() },
                    enabled = !isLoading,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, TextGray),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = Color.Transparent,
                        contentColor = TextGray,
                        disabledContentColor = TextGray.copy(alpha = 0.5f)
                    )
                ) {
                    Text("Cancel")
                }
                Button(
                    onClick = { onConfirm(reason.ifBlank { "Admin forced exit" }) },
                    colors = ButtonDefaults.buttonColors(containerColor = DangerRed),
                    shape = RoundedCornerShape(8.dp),
                    enabled = !isLoading
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Confirm Exit", color = Color.White)
                    }
                }
            }
        },
        shape = RoundedCornerShape(16.dp)
    )
}
