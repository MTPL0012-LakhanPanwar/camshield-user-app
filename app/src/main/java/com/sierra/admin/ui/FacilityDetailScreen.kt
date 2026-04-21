package com.camshield.admin.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.camshield.admin.viewmodel.FacilityViewModel
import com.sierra.admin.modal.ApiResult
import com.sierra.admin.modal.FacilityData
import com.sierra.admin.modal.QRData
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val FdBgDark = Color(0xFF0B101F)
private val FdCardBg = Color(0xFF161C2C)
private val FdAccentBlue = Color(0xFF2196F3)
private val FdTextGray = Color(0xFF8A92A6)
private val FdStatusGreen = Color(0xFF4CAF50)
private val FdStatusRed = Color(0xFFEF5350)
private val FdChipBg = Color(0xFF1D2536)

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun FacilityDetailScreen(
    facilityId: String,
    viewModel: FacilityViewModel,
    onNavigateBack: () -> Unit,
    onEditFacility: (String) -> Unit,
    onDeleteSuccess: () -> Unit,
    onUnauthorized: () -> Unit
    ) {
    val detailState by viewModel.detailState.collectAsState()
    val deleteState by viewModel.deleteState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var confirmDelete by remember { mutableStateOf(false) }
    var qrFocus by remember { mutableStateOf<QRType?>(null) }

    LaunchedEffect(facilityId) { viewModel.loadDetail(facilityId) }

    LaunchedEffect(detailState) {
        if (detailState is ApiResult.Error && (detailState as ApiResult.Error).code == 401) {
            onUnauthorized()
        }
    }

    LaunchedEffect(deleteState) {
        when (val state = deleteState) {
            is ApiResult.Success -> {
                scope.launch { snackbarHostState.showSnackbar(state.data) }
                viewModel.resetDelete()
                viewModel.resetDetail()
                onDeleteSuccess()
            }
            is ApiResult.Error -> {
                scope.launch { snackbarHostState.showSnackbar(state.message) }
                if (state.code == 401) onUnauthorized()
                viewModel.resetDelete()
            }
            else -> {}
        }
    }

    DisposableEffect(Unit) {
        onDispose { viewModel.resetDetail(); viewModel.resetQR() }
    }

    Scaffold(
        containerColor = FdBgDark,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        "FACILITY DETAILS",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp,
                        color = Color.White
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { onNavigateBack() }) {
                        Icon(Icons.Default.ArrowBack, null, tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = FdBgDark)
            )
        },
        snackbarHost = {
            SnackbarHost(snackbarHostState) { data ->
                Snackbar(snackbarData = data, containerColor = Color(0xFF2E7D32), contentColor = Color.White)
            }
        }
    ) { innerPadding ->
        when (detailState) {
            is ApiResult.Loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) { CircularProgressIndicator(color = FdAccentBlue) }
            }
            is ApiResult.Error -> {
                val err = detailState as ApiResult.Error
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(20.dp)
                    ) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = FdStatusRed)
                        Text(
                            if (err.code == 404) "Facility not found" else err.message.ifBlank { "Couldn’t load facility details." },
                            color = FdStatusRed,
                            textAlign = TextAlign.Center
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedButton(
                                onClick = { onNavigateBack() },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                            ) { Text("Go Back") }
                            Button(
                                onClick = { viewModel.loadDetail(facilityId) },
                                colors = ButtonDefaults.buttonColors(containerColor = FdAccentBlue)
                            ) { Text("Retry") }
                        }
                    }
                }
            }
            is ApiResult.Success -> {
                val facility = (detailState as ApiResult.Success<FacilityData>).data
                FacilityDetailContent(
                    facility = facility,
                    paddingValues = innerPadding,
                    onEdit = { onEditFacility(facility.id) },
                    onDelete = { confirmDelete = true },
                    onShowEntry = { qrFocus = QRType.ENTRY },
                    onShowExit = { qrFocus = QRType.EXIT }
                )
            }
            else -> {}
        }
    }

    if (confirmDelete && detailState is ApiResult.Success) {
        val facility = (detailState as ApiResult.Success<FacilityData>).data
        OpenDeleteConfirmationDialog(
            facilityName = facility.name,
            isDeleting = deleteState is ApiResult.Loading,
            onDismissRequest = { if (deleteState !is ApiResult.Loading) confirmDelete = false },
            onConfirmDelete = {
                if (deleteState !is ApiResult.Loading) {
                    viewModel.deleteFacility(facility.id)
                }
            }
        )
    }

    if (qrFocus != null && detailState is ApiResult.Success) {
        val facility = (detailState as ApiResult.Success<FacilityData>).data
        QRCodeDialog(
            facility = facility,
            viewModel = viewModel,
            focus = qrFocus,
            onDismiss = { qrFocus = null },
            showSnackbar = { msg -> scope.launch { snackbarHostState.showSnackbar(msg) } }
        )
    }
}

@Composable
private fun FacilityDetailContent(
    facility: FacilityData,
    paddingValues: PaddingValues,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onShowEntry: () -> Unit,
    onShowExit: () -> Unit
) {
    val scroll = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .verticalScroll(scroll)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        SummaryCard(facility)

        facility.description?.takeIf { it.isNotBlank() }?.let { desc ->
            InfoCard(title = "Description", content = desc)
        }

        InfoCard(title = "Location") {
            val loc = facility.location
            if (loc == null || (loc.address + loc.city + loc.state + loc.country).isBlank()) {
                Text("Not provided", color = FdTextGray, fontSize = 13.sp)
            } else {
                loc.address.takeIf { it.isNotBlank() }?.let { LabelValue("Street", it) }
                loc.city.takeIf { it.isNotBlank() }?.let { LabelValue("City", it) }
                loc.state.takeIf { it.isNotBlank() }?.let { LabelValue("State / Province", it) }
                loc.country.takeIf { it.isNotBlank() }?.let { LabelValue("Country", it) }
            }
        }

        if (facility.notificationEmails.isNotEmpty()) {
            InfoCard(title = "Notification Emails") {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    facility.notificationEmails.forEach { email ->
                        TagChip(email)
                    }
                }
            }
        }

        ActiveQRCard(
            entry = facility.activeQRCodes.firstOrNull { it.type.equals("entry", true) },
            exit = facility.activeQRCodes.firstOrNull { it.type.equals("exit", true) },
            onShowEntry = onShowEntry,
            onShowExit = onShowExit
        )

        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Button(
                onClick = onEdit,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = FdAccentBlue),
                shape = RoundedCornerShape(10.dp)
            ) { Text("Update Facility", fontWeight = FontWeight.SemiBold) }
            OutlinedButton(
                onClick = onDelete,
                modifier = Modifier.weight(1f),
                border = BorderStroke(1.dp, FdStatusRed),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = FdStatusRed),
                shape = RoundedCornerShape(10.dp)
            ) { Text("Delete", color = FdStatusRed, fontWeight = FontWeight.SemiBold) }
        }
    }
}

@Composable
private fun SummaryCard(facility: FacilityData) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = FdCardBg)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(facility.name, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
                StatusBadge(facility.status)
            }
            facility.timezone?.takeIf { it.isNotBlank() }?.let {
                TagChip("Timezone: $it")
            }
            facility.createdAt?.takeIf { it.isNotBlank() }?.let {
                Text("Created: ${formatDateTimeFriendly(it)}", color = FdTextGray, fontSize = 12.sp)
            }
            facility.updatedAt?.takeIf { it.isNotBlank() }?.let {
                Text("Updated: ${formatDateTimeFriendly(it)}", color = FdTextGray, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun InfoCard(title: String, content: String? = null, body: (@Composable () -> Unit)? = null) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = FdCardBg)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            body?.invoke() ?: Text(content.orEmpty(), color = FdTextGray, fontSize = 13.sp)
        }
    }
}

@Composable
private fun LabelValue(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, color = FdTextGray, fontSize = 12.sp)
        Text(value, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun TagChip(text: String) {
    Box(
        modifier = Modifier
            .background(FdChipBg, RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(text, color = Color.White, fontSize = 12.sp)
    }
}

@Composable
private fun StatusBadge(status: String) {
    val isActive = status.equals("active", ignoreCase = true)
    Box(
        modifier = Modifier
            .background(
                color = if (isActive) FdStatusGreen.copy(alpha = 0.15f) else FdStatusRed.copy(alpha = 0.15f),
                shape = RoundedCornerShape(8.dp)
            )
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            text = status.ifBlank { "UNKNOWN" }.uppercase(),
            color = if (isActive) FdStatusGreen else FdStatusRed,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun ActiveQRCard(
    entry: QRData?,
    exit: QRData?,
    onShowEntry: () -> Unit,
    onShowExit: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = FdCardBg)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Active QR Codes", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)

            QRRow(label = "Entry", qr = entry, onShow = onShowEntry)
            QRRow(label = "Exit", qr = exit, onShow = onShowExit)
        }
    }
}

@Composable
private fun QRRow(label: String, qr: QRData?, onShow: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, color = FdTextGray, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        if (qr == null) {
            Text("No active $label QR", color = FdTextGray, fontSize = 12.sp)
            return
        }
        val status = qr.status.ifBlank { "active" }
        val validRaw = qr.generatedForDate?.takeIf { it.isNotBlank() }
            ?: qr.validUntil?.takeIf { it.isNotBlank() }
            ?: qr.validFrom?.takeIf { it.isNotBlank() }
        val validFor = validRaw?.let { formatDateTimeFriendly(it) }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StatusBadge(status)
            validFor?.let { Text("Valid For: ${formatDateTimeFriendly2(it)}", color = FdTextGray, fontSize = 12.sp) }
            Spacer(Modifier.weight(1f))
            Button(
                onClick = onShow,
                colors = ButtonDefaults.buttonColors(
                    containerColor = FdAccentBlue,
                    disabledContainerColor = FdChipBg,
                    contentColor = Color.White,
                    disabledContentColor = FdTextGray
                ),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("${label} QR", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

private val facilityFriendlyFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("dd MMM yyyy, h:mm a")
private fun formatDateTimeFriendly(raw: String): String = runCatching {
    facilityFriendlyFormatter.format(ZonedDateTime.ofInstant(Instant.parse(raw), ZoneId.systemDefault()))
}.getOrElse {
    runCatching {
        val cleaned = raw.removeSuffix("Z")
        facilityFriendlyFormatter.format(LocalDateTime.parse(cleaned).atZone(ZoneId.systemDefault()))
    }.getOrElse {
        raw.replace("T", " ").take(19)
    }
}
// Example: "30 Mar 2026"
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
