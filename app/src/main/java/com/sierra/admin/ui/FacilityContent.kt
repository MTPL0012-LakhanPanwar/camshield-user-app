package com.camshield.admin.ui

import android.content.Intent
import androidx.activity.result.ActivityResultLauncher
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.camshield.admin.viewmodel.FacilityViewModel
import com.sierra.admin.activity.CreateUpdateFacility
import com.sierra.admin.activity.FacilityDetailActivity
import com.sierra.admin.modal.ApiResult
import com.sierra.admin.modal.FacilityData
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

private val FcBgDark = Color(0xFF0B101F)
private val FcCardBg = Color(0xFF161C2C)
private val FcAccentBlue = Color(0xFF2196F3)
private val FcTextGray = Color(0xFF8A92A6)
private val FcStatusGreen = Color(0xFF4CAF50)
private val FcStatusRed = Color(0xFFEF5350)

@OptIn(FlowPreview::class, ExperimentalMaterialApi::class)
@Composable
fun FacilityContent(
    viewModel: FacilityViewModel,
    showSnackbar: (String) -> Unit,
    onUnauthorized: () -> Unit,
    facilityDetailLauncher: ActivityResultLauncher<Intent>,
    createUpdateLauncher: ActivityResultLauncher<Intent>
) {
    var searchQuery by remember { mutableStateOf("") }
    val context = LocalContext.current
    val listState by viewModel.listState.collectAsState()
    val deleteState by viewModel.deleteState.collectAsState()
    val lazyListState = rememberLazyListState()
    val isLoading = listState is ApiResult.Loading
    val isRefreshing = isLoading && viewModel.items.isNotEmpty()
    var facilityToDelete by remember { mutableStateOf<FacilityData?>(null) }
    var facilityForQR by remember { mutableStateOf<Pair<FacilityData, QRType>?>(null) }
    val pullRefreshState = rememberPullRefreshState(
        refreshing = isRefreshing,
        onRefresh = { viewModel.refreshFacilities() }
    )

    // Search debounce — drop(1) skips the initial StateFlow value to avoid
    // a duplicate load race with the explicit initial-load LaunchedEffect below.
    val searchFlow = remember { MutableStateFlow("") }
    LaunchedEffect(Unit) {
        searchFlow.drop(1).debounce(400).collect { q ->
            viewModel.loadFacilities(1, q, reset = true)
        }
    }
    LaunchedEffect(searchQuery) { searchFlow.value = searchQuery }

    LaunchedEffect(Unit) {
        if (viewModel.items.isEmpty()) viewModel.loadFacilities(reset = true)
    }

    LaunchedEffect(listState) {
        if (listState is ApiResult.Error && (listState as ApiResult.Error).code == 401) onUnauthorized()
    }

    LaunchedEffect(deleteState) {
        when (val state = deleteState) {
            is ApiResult.Success -> {
                showSnackbar(state.data)
                // Remove the deleted facility from the local list immediately
                facilityToDelete?.let { deleted ->
                    viewModel.items.removeAll { it.id == deleted.id }
                }
                facilityToDelete = null
                viewModel.resetDelete()
                viewModel.refreshFacilities()
            }
            is ApiResult.Error -> {
                showSnackbar(state.message)
                facilityToDelete = null
                viewModel.resetDelete()
                if (state.code == 401) onUnauthorized()
            }
            else -> {}
        }
    }

    // Infinite scroll trigger
    val shouldLoadMore by remember {
        derivedStateOf {
            val lastVisible = lazyListState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisible >= viewModel.items.size - 3 && !viewModel.isLastPage && !isLoading
        }
    }
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) viewModel.loadNextPage()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(FcBgDark)
            .pullRefresh(pullRefreshState)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
        // Action Header
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Search Facility...", color = FcTextGray) },
                leadingIcon = { Icon(Icons.Default.Search, null, tint = FcTextGray) },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedContainerColor = FcCardBg,
                    unfocusedContainerColor = FcCardBg,
                    focusedBorderColor = FcAccentBlue,
                    unfocusedBorderColor = Color.Transparent
                )
            )
            Button(
                onClick = {
                    val intent = Intent(context, CreateUpdateFacility::class.java)
                    createUpdateLauncher.launch(intent)
                },
                modifier = Modifier.height(46.dp),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = FcAccentBlue)
            ) {
                Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Create", fontSize = 13.sp)
            }
        }

            when {
                isLoading && viewModel.items.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = FcAccentBlue)
                    }
                }
                listState is ApiResult.Error && viewModel.items.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                            val err = listState as ApiResult.Error
                            Text(
                                if (err.code == 404) "There are no facilities available." else err.message.ifBlank { "Couldn’t load facilities. Pull to refresh or try again." },
                                color = FcStatusRed,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                            Spacer(Modifier.height(12.dp))
                            Button(
                                onClick = { viewModel.refreshFacilities() },
                                colors = ButtonDefaults.buttonColors(containerColor = FcAccentBlue)
                            ) { Text("Retry") }
                        }
                    }
                }
                viewModel.items.isEmpty() && !isLoading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("There are no facilities available", color = FcTextGray, fontSize = 16.sp)
                            Spacer(Modifier.height(12.dp))
                            Button(
                                onClick = {
                                    val intent = Intent(context, CreateUpdateFacility::class.java)
                                    createUpdateLauncher.launch(intent)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = FcAccentBlue)
                            ) { Text("Create First Facility") }
                        }
                    }
                }
                else -> {
                    LazyColumn(state = lazyListState, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(viewModel.items) { facility ->
                            FacilityCardComposable(
                                facility = facility,
                                onUpdate = {
                                    val intent = Intent(context, CreateUpdateFacility::class.java)
                                    intent.putExtra(CreateUpdateFacility.EXTRA_FACILITY_ID, facility.id)
                                    createUpdateLauncher.launch(intent)
                                },
                                onDelete = { facilityToDelete = facility },
                                onQRCode = { qrType -> facilityForQR = facility to qrType },
                                onView = {
                                    if (facility.id.isNotBlank()) {
                                        val intent = Intent(context, FacilityDetailActivity::class.java)
                                        intent.putExtra(FacilityDetailActivity.EXTRA_FACILITY_ID, facility.id)
                                        facilityDetailLauncher.launch(intent)
                                    }
                                }
                            )
                        }
                        if (isLoading) {
                            item {
                                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator(color = FcAccentBlue, modifier = Modifier.size(24.dp))
                                }
                            }
                        }
                        if (viewModel.isLastPage && viewModel.items.isNotEmpty()) {
                            item {
                                Box(Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) {
                                    Text("${viewModel.items.size} facilities total", color = FcTextGray, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }
        }

        PullRefreshIndicator(
            refreshing = isRefreshing,
            state = pullRefreshState,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 8.dp),
            contentColor = FcAccentBlue,
            backgroundColor = FcCardBg
        )
    }

    // Delete confirmation dialog
    facilityToDelete?.let { facility ->
        OpenDeleteConfirmationDialog(
            facilityName = facility.name,
            isDeleting = deleteState is ApiResult.Loading,
            onDismissRequest = { if (deleteState !is ApiResult.Loading) facilityToDelete = null },
            onConfirmDelete = {
                viewModel.deleteFacility(facility.id)
            }
        )
    }

    // QR code dialog
    facilityForQR?.let { (facility, qrType) ->
        QRCodeDialog(
            facility = facility,
            viewModel = viewModel,
            focus = qrType,
            onDismiss = { facilityForQR = null },
            showSnackbar = showSnackbar
        )
    }
}

@Composable
fun FacilityCardComposable(
    facility: FacilityData,
    onUpdate: () -> Unit,
    onDelete: () -> Unit,
    onQRCode: (QRType) -> Unit,
    onView: () -> Unit = {}
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onView() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = FcCardBg)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = facility.name,
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f).padding(end = 8.dp)
                )
                FacilityStatusBadge(facility.status)
            }

            if (!facility.description.isNullOrBlank()) {
                Text(
                    text = facility.description,
                    color = FcTextGray,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 6.dp),
                    maxLines = 2
                )
            }

            facility.location?.let { loc ->
                val locationStr = listOf(loc.city, loc.state, loc.country).filter { it.isNotBlank() }.joinToString(", ")
                if (locationStr.isNotBlank()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 6.dp)
                    ) {
                        Icon(Icons.Default.Place, null, tint = FcTextGray, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(locationStr, color = FcTextGray, fontSize = 12.sp)
                    }
                }
            }

            if (facility.notificationEmails.isNotEmpty()) {
                Text(
                    "${facility.notificationEmails.size} notification email(s)",
                    color = FcTextGray,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            if (!facility.createdAt.isNullOrBlank()) {
                Text(
                    "Created: ${formatDateFriendly(facility.createdAt)}",
                    color = FcTextGray,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    QRAction(iconLabel = "Entry", onClick = { onQRCode(QRType.ENTRY) })
                    QRAction(iconLabel = "Exit", onClick = { onQRCode(QRType.EXIT) })
                }
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(
                        onClick = onDelete,
                        border = BorderStroke(1.dp, FcStatusRed),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = FcStatusRed,
                            containerColor = Color.Transparent
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, tint = FcStatusRed, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Delete", fontSize = 13.sp)
                    }
                    Button(
                        onClick = onUpdate,
                        colors = ButtonDefaults.buttonColors(containerColor = FcAccentBlue),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Update", color = Color.White, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun QRAction(iconLabel: String, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        IconButton(onClick = onClick, modifier = Modifier.size(40.dp)) {
            Icon(
                Icons.Default.QrCode,
                contentDescription = "$iconLabel QR",
                tint = FcAccentBlue,
                modifier = Modifier.size(36.dp)
            )
        }
        Text(iconLabel, color = FcTextGray, fontSize = 12.sp)
    }
}

@Composable
private fun FacilityStatusBadge(status: String) {
    val isActive = status.lowercase() == "active"
    Box(
        modifier = Modifier
            .background(
                color = if (isActive) FcStatusGreen.copy(alpha = 0.15f) else FcStatusRed.copy(alpha = 0.15f),
                shape = RoundedCornerShape(6.dp)
            )
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(
            text = status.uppercase(),
            color = if (isActive) FcStatusGreen else FcStatusRed,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

private val facilityCardFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("dd MMM yyyy")
private fun formatDateFriendly(raw: String): String = runCatching {
    facilityCardFormatter.format(ZonedDateTime.ofInstant(Instant.parse(raw), ZoneId.systemDefault()))
}.getOrElse {
    runCatching {
        val cleaned = raw.removeSuffix("Z")
        facilityCardFormatter.format(LocalDateTime.parse(cleaned).atZone(ZoneId.systemDefault()))
    }.getOrElse { raw.take(10) }
}

@Composable
fun OpenDeleteConfirmationDialog(
    facilityName: String = "",
    isDeleting: Boolean = false,
    onDismissRequest: () -> Unit,
    onConfirmDelete: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        containerColor = Color(0xFF161C2C),
        title = {
            Text("Delete Facility", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        },
        text = {
            Text(
                "Are you sure you want to permanently delete \"$facilityName\"? This cannot be undone.",
                color = Color.Gray,
                fontSize = 15.sp
            )
        },
        // Centered action row
        confirmButton = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)
            ) {
                OutlinedButton(
                    onClick = onDismissRequest,
                    enabled = !isDeleting,
                    border = BorderStroke(1.dp, Color.Gray),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color.Gray,
                        containerColor = Color.Transparent,
                        disabledContentColor = Color.Gray.copy(alpha = 0.5f)
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Cancel")
                }
                Button(
                    onClick = onConfirmDelete,
                    colors = ButtonDefaults.buttonColors(containerColor = FcStatusRed),
                    shape = RoundedCornerShape(8.dp),
                    enabled = !isDeleting
                ) {
                    if (isDeleting) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Delete", color = Color.White)
                    }
                }
            }
        },
        shape = RoundedCornerShape(16.dp)
    )
}

@Preview(showBackground = true, backgroundColor = 0xFF0B101F)
@Composable
fun PreviewFacilityCard() {
    val mockFacility = FacilityData(
        id = "1",
        name = "Global Innovation Center",
        description = "Main research facility for security protocols and hardware testing.",
        status = "Active",
        location = null, // You can fill this if your modal supports it
        notificationEmails = listOf("admin@example.com"),
        createdAt = "2023-10-27T10:00:00Z"
    )

    Box(modifier = Modifier.padding(16.dp)) {
        FacilityCardComposable(
            facility = mockFacility,
            onUpdate = {},
            onDelete = {},
            onQRCode = {},
            onView = {}
        )
    }
}
