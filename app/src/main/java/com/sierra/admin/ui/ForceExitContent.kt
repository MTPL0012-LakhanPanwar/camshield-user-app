package com.camshield.admin.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.camshield.admin.viewmodel.DeviceViewModel
import com.sierra.admin.modal.ActiveDeviceItem
import com.sierra.admin.modal.ApiResult
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.debounce
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

private val FxBgDark = Color(0xFF0B101F)
private val FxCardBg = Color(0xFF161C2C)
private val FxAccentBlue = Color(0xFF2196F3)
private val FxTextGray = Color(0xFF8A92A6)
private val FxStatusGreen = Color(0xFF4CAF50)

@Composable
private fun FxStatusBadge(status: String) {
    val isActive = status.equals("active", ignoreCase = true)
    val bg = if (isActive) FxStatusGreen.copy(alpha = 0.15f) else Color(0xFF455A64).copy(alpha = 0.3f)
    val fg = if (isActive) FxStatusGreen else Color(0xFFE0E0E0)
    Box(
        modifier = Modifier
            .background(bg, RoundedCornerShape(12.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(status.ifBlank { "UNKNOWN" }.uppercase(), color = fg, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun InfoTag(text: String) {
    Box(
        modifier = Modifier
            .background(Color(0xFF20293A), RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(text, color = Color.White, fontSize = 11.sp)
    }
}

@OptIn(FlowPreview::class, ExperimentalMaterialApi::class)
@Composable
fun ForceExitContent(
    navController: NavHostController,
    viewModel: DeviceViewModel,
    onUnauthorized: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val listState by viewModel.listState.collectAsState()
    val lazyListState = rememberLazyListState()
    val isLoading = listState is ApiResult.Loading
    val isRefreshing = isLoading && viewModel.items.isNotEmpty()
    val pullRefreshState = rememberPullRefreshState(
        refreshing = isRefreshing,
        onRefresh = { viewModel.refreshDevices() }
    )

    val searchFlow = remember { MutableStateFlow("") }
    LaunchedEffect(Unit) {
        searchFlow.debounce(400).collect { q ->
            viewModel.loadDevices(1, q, reset = true)
        }
    }
    LaunchedEffect(searchQuery) { searchFlow.value = searchQuery }

    LaunchedEffect(Unit) {
        if (viewModel.items.isEmpty()) viewModel.loadDevices(reset = true)
    }

    LaunchedEffect(listState) {
        if (listState is ApiResult.Error && (listState as ApiResult.Error).code == 401) {
            onUnauthorized()
        }
    }

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
            .background(FxBgDark)
            .pullRefresh(pullRefreshState)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Search by device or visitor ID…", color = FxTextGray) },
                leadingIcon = { Icon(Icons.Default.Search, null, tint = FxTextGray) },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedContainerColor = FxCardBg,
                    unfocusedContainerColor = FxCardBg,
                    focusedBorderColor = FxAccentBlue,
                    unfocusedBorderColor = Color.Transparent
                )
            )
        }

            when {
                isLoading && viewModel.items.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = FxAccentBlue)
                    }
                }
                listState is ApiResult.Error && viewModel.items.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                            val err = (listState as ApiResult.Error)
                            Text(
                                if (err.code == 404) "There are currently no active devices connected." else err.message.ifBlank { "Couldn’t load devices. Pull to refresh or try again." },
                                color = Color(0xFFEF5350),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                            Spacer(Modifier.height(12.dp))
                            Button(
                                onClick = { viewModel.refreshDevices() },
                                colors = ButtonDefaults.buttonColors(containerColor = FxAccentBlue)
                            ) { Text("Retry") }
                        }
                    }
                }
                viewModel.items.isEmpty() && !isLoading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("There are currently no active devices connected", color = FxTextGray, fontSize = 16.sp)
                    }
                }
                else -> {
                    LazyColumn(state = lazyListState, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(
                            items = viewModel.items,
                            key = { it.id }
                        ) { device: ActiveDeviceItem ->
                            ActiveDeviceCardItem(device) {
                                val targetId = device.device.deviceId.ifBlank { device.id }
                                if (targetId.isNotBlank()) {
                                    viewModel.selectDevice(device)
                                    navController.navigate("device/$targetId")
                                }
                            }
                        }
                        if (isLoading) {
                            item {
                                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator(color = FxAccentBlue, modifier = Modifier.size(24.dp))
                                }
                            }
                        }
                        if (viewModel.isLastPage && viewModel.items.isNotEmpty()) {
                            item {
                                Box(Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) {
                                    Text("${viewModel.items.size} active devices", color = FxTextGray, fontSize = 12.sp)
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
            contentColor = FxAccentBlue,
            backgroundColor = FxCardBg
        )
    }
}

@Composable
private fun ActiveDeviceCardItem(device: ActiveDeviceItem, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = FxCardBg)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(FxAccentBlue.copy(alpha = 0.15f), RoundedCornerShape(24.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.PhoneAndroid, null, tint = FxAccentBlue, modifier = Modifier.size(26.dp))
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        device.device.deviceName.ifBlank { "Unknown Device" },
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                        modifier = Modifier.weight(1f)
                    )
                    FxStatusBadge(device.status.ifBlank { device.device.status })
                }
                Text(
                    device.device.model.ifBlank { "Model: Unknown" },
                    color = FxTextGray,
                    fontSize = 12.sp
                )
                val platform = device.device.platform.ifBlank { "Unknown" }
                val platformLabel = if (device.device.osVersion.isNotBlank()) " • OS ${device.device.osVersion}" else ""
                Text(
                    "Platform: ${platform.uppercase()}$platformLabel",
                    color = FxTextGray,
                    fontSize = 12.sp
                )
                val visitorLabel = device.visitorId.ifBlank { "Unknown" }
                InfoTag("Visitor: $visitorLabel")
                val lastSeen = device.lastActivity ?: device.updatedAt ?: device.createdAt
                val formattedLastSeen = lastSeen?.let { formatDateTimeFriendly(it) } ?: "—"

                Text(
                    text = "Last active: $formattedLastSeen",
                    color = FxTextGray,
                    fontSize = 12.sp
                )
                device.currentFacility?.let {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("At:", color = FxTextGray, fontSize = 12.sp)
                        Text(it.name, color = FxAccentBlue, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }
}

private val friendlyFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("dd MMM yyyy, h:mm a")

private fun formatDateTimeFriendly(raw: String): String = runCatching {
    friendlyFormatter.format(ZonedDateTime.ofInstant(Instant.parse(raw), ZoneId.systemDefault()))
}.getOrElse {
    runCatching {
        val cleaned = raw.removeSuffix("Z")
        friendlyFormatter.format(LocalDateTime.parse(cleaned).atZone(ZoneId.systemDefault()))
    }.getOrElse {
        raw.replace("T", " ").take(19)
    }
}
