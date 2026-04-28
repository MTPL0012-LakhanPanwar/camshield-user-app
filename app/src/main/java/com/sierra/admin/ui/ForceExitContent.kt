package com.camshield.admin.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.camshield.admin.viewmodel.DeviceViewModel
import com.sierra.admin.modal.ActiveDeviceItem
import com.sierra.admin.modal.ApiResult
import com.sierra.admin.modal.DeviceInfo
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.debounce
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

private val FxBgDark = Color(0xFF0B101F)
private val FxCardBg = Color(0xFF161C2C)
private val FxAccentBlue = Color(0xFF2196F3)
private val FxTextGray = Color(0xFF8A92A6)

private val FxPanelBg = Color(0xFF121A2B)
private val FxPanelBorder = Color(0xFF28324A)
private val FxSearchBg = Color(0xFF1A2336)
private val FxChipBg = Color(0xFF1A2234)
private val FxChipBgActive = Color(0xFF24344F)
private val FxChipBorder = Color(0xFF303A52)
private val FxChipBorderActive = Color(0xFF47608E)

private val FxDeviceCardBg = Color(0xFF121B2D)
private val FxDeviceCardBorder = Color(0xFF2A3450)
private val FxDeviceCardDivider = Color(0xFF2B354C)
private val FxPrimaryText = Color(0xFFE6ECF8)
private val FxSecondaryText = Color(0xFFB5BECC)
private val FxLabelText = Color(0xFF7B889F)
private val FxVisitorTagBg = Color(0xFF27354C)
private val FxVisitorTagText = Color(0xFFA9CCFF)

private val FxStatusActiveText = Color(0xFF7BEEA8)
private val FxStatusInactiveText = Color(0xFFFFB3A3)
private val FxStatusActiveBg = Color(0xFF1A4A33)
private val FxStatusInactiveBg = Color(0xFF4A2A2A)
private val FxStatusActiveBorder = Color(0xFF2E7E58)
private val FxStatusInactiveBorder = Color(0xFF8A2F3A)

@Composable
private fun FxStatusBadge(status: String) {
    val isActive = status.equals("active", ignoreCase = true)
    val bg = if (isActive) FxStatusActiveBg else FxStatusInactiveBg
    val border = if (isActive) FxStatusActiveBorder else FxStatusInactiveBorder
    val fg = if (isActive) FxStatusActiveText else FxStatusInactiveText
    val label = if (isActive) "ENTRY DONE" else "EXIT LOGGED"

    Box(
        modifier = Modifier
            .border(BorderStroke(1.dp, border), RoundedCornerShape(46.dp))
            .background(bg, RoundedCornerShape(46.dp))
            .padding(vertical = 4.dp, horizontal = 12.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(
            text = label,
            color = fg,
            fontSize = 10.sp,
            lineHeight = 15.sp,
            letterSpacing = 1.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun InfoTag(text: String) {
    Box(
        modifier = Modifier
            .background(FxVisitorTagBg, RoundedCornerShape(4.dp))
            .padding(horizontal = 8.dp, vertical = 2.dp)
    ) {
        Text(text,
            color = FxVisitorTagText,
            fontSize = 11.sp,
            lineHeight = 17.sp,
            fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun ForceExitFilterHeader(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    selectedDate: String?,
    onDateClick: () -> Unit,
    onClearDate: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp),
        shape = RoundedCornerShape(22.dp),
        color = FxPanelBg,
        border = BorderStroke(1.dp, FxPanelBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                placeholder = {
                    Text(
                        "Search by device or visitor ID...",
                        color = FxTextGray,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                leadingIcon = { Icon(Icons.Default.Search, null, tint = FxTextGray) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedContainerColor = FxSearchBg,
                    unfocusedContainerColor = FxSearchBg,
                    focusedBorderColor = FxChipBorderActive,
                    unfocusedBorderColor = Color.Transparent
                )
            )

            Row(horizontalArrangement = Arrangement.Start) {
                Surface(
                    modifier = Modifier
                        .height(IntrinsicSize.Max)
                        .clickable { onDateClick() },
                    shape = RoundedCornerShape(27.dp),
                    color = if (selectedDate.isNullOrBlank()) FxChipBg else FxChipBgActive,
                    border = BorderStroke(
                        1.dp,
                        if (selectedDate.isNullOrBlank()) FxChipBorder else FxChipBorderActive
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxHeight()
                            .padding(vertical = 6.dp, horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val dateTint = if (selectedDate.isNullOrBlank()) FxTextGray else Color(0xFFA7CBFF)
                        Icon(Icons.Default.DateRange,
                            contentDescription = null,
                            tint = dateTint,
                            modifier = Modifier.size(16.dp)
                        )

                        Text(
                            text = formatDateFilterLabel(selectedDate),
                            color = if (selectedDate.isNullOrBlank()) FxTextGray else Color(0xFFA7CBFF),
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        if (!selectedDate.isNullOrBlank()) {
                            IconButton(
                                onClick = onClearDate,
                                modifier = Modifier.size(30.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Clear date",
                                    tint = FxTextGray,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
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
    var selectedDate by remember { mutableStateOf<String?>(null) }
    var showDatePicker by remember { mutableStateOf(false) }
    val listState by viewModel.listState.collectAsState()
    val lazyListState = rememberLazyListState()
    val isLoading = listState is ApiResult.Loading
    val isRefreshing = isLoading && viewModel.items.isNotEmpty()
    val selectedDateForSearch by rememberUpdatedState(selectedDate)
    val pullRefreshState = rememberPullRefreshState(
        refreshing = isRefreshing,
        onRefresh = { viewModel.refreshDevices() }
    )

    val searchFlow = remember { MutableStateFlow("") }
    LaunchedEffect(Unit) {
        searchFlow.debounce(400).collect { q ->
            viewModel.loadDevices(1, q, selectedDateForSearch.orEmpty(), reset = true)
        }
    }
    LaunchedEffect(searchQuery) { searchFlow.value = searchQuery }

    LaunchedEffect(Unit) {
        if (viewModel.items.isEmpty()) viewModel.loadDevices(reset = true, date = selectedDate.orEmpty())
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
            ForceExitFilterHeader(
                searchQuery = searchQuery,
                onSearchQueryChange = { searchQuery = it },
                selectedDate = selectedDate,
                onDateClick = { showDatePicker = true },
                onClearDate = {
                    selectedDate = null
                    viewModel.loadDevices(1, searchQuery, reset = true)
                }
            )

            if (showDatePicker) {
                val todayUtcMillis = remember {
                    LocalDate.now(ZoneOffset.UTC)
                        .atStartOfDay()
                        .toInstant(ZoneOffset.UTC)
                        .toEpochMilli()
                }
                val datePickerState = rememberDatePickerState(
                    initialSelectedDateMillis = selectedDate?.let(::parseApiDateToUtcMillis),
                    selectableDates = object : SelectableDates {
                        override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                            return utcTimeMillis <= todayUtcMillis
                        }
                    }
                )

                DatePickerDialog(
                    onDismissRequest = { showDatePicker = false },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                val pickedDate = datePickerState.selectedDateMillis?.let(::formatApiDate)
                                showDatePicker = false
                                if (!pickedDate.isNullOrBlank() && pickedDate != selectedDate) {
                                    selectedDate = pickedDate
                                    viewModel.loadDevices(1, searchQuery, pickedDate, reset = true)
                                }
                            }
                        ) {
                            Text("Apply")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showDatePicker = false }) {
                            Text("Cancel")
                        }
                    }
                ) {
                    DatePicker(
                        state = datePickerState,
                        showModeToggle = false
                    )
                }
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
                                if (err.code == 404) {
                                    "There are currently no active devices connected."
                                } else {
                                    err.message.ifBlank { "Couldn’t load devices. Pull to refresh or try again." }
                                },
                                color = Color(0xFFEF5350),
                                textAlign = TextAlign.Center
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
                    LazyColumn(state = lazyListState, verticalArrangement = Arrangement.spacedBy(14.dp)) {
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
    val modelAndManufacturer = buildString {
        append(device.device.model.ifBlank { "Unknown Model" })
        if (device.device.manufacturer.isNotBlank()) {
            append(" • ")
            append(device.device.manufacturer)
        }
    }
    val platformTitle = device.device.platform.ifBlank { "android" }
    val normalizedPlatform = platformTitle.replaceFirstChar {
        if (it.isLowerCase()) it.titlecase() else it.toString()
    }
    val platformDisplay = if (device.device.osVersion.isNotBlank()) {
        "$normalizedPlatform OS ${device.device.osVersion}"
    } else {
        normalizedPlatform
    }
    val visitorLabel = device.visitorId.ifBlank { "Visitor-NA" }
    val lastSeen = device.lastActivity ?: device.updatedAt ?: device.createdAt
    val formattedLastSeen = lastSeen?.let {
        formatDateTimeFriendly(it).replace("AM", "am").replace("PM", "pm")
    } ?: "No activity yet"

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = FxDeviceCardBg),
        border = BorderStroke(1.dp, FxDeviceCardBorder)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(FxSearchBg, RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.PhoneAndroid, null, tint = Color(0xFF9FC6FF), modifier = Modifier.size(22.dp))
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = device.device.deviceName.ifBlank { "Unknown" },
                        color = FxPrimaryText,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        lineHeight = 24.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = modelAndManufacturer,
                        color = FxSecondaryText,
                        fontWeight = FontWeight.Medium,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                FxStatusBadge(device.status.ifBlank { device.device.status })
            }

            HorizontalDivider(color = FxDeviceCardDivider, thickness = 1.dp)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "PLATFORM",
                        color = FxLabelText,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        lineHeight = 15.sp,
                        letterSpacing = 0.5.sp
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Android,
                            contentDescription = null,
                            tint = Color(0xFF9FC6FF),
                            modifier = Modifier.size(22.dp)
                        )
                        Text(
                            text = platformDisplay,
                            color = FxPrimaryText,
                            fontWeight = FontWeight.Medium,
                            fontSize = 14.sp,
                            lineHeight = 20.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "VISITOR",
                        color = FxLabelText,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        lineHeight = 15.sp,
                        letterSpacing = 0.5.sp
                    )

                    InfoTag(visitorLabel)
                }
            }

            HorizontalDivider(color = FxDeviceCardDivider, thickness = 1.dp)

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.History,
                    contentDescription = null,
                    tint = FxLabelText,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = formattedLastSeen,
                    color = FxSecondaryText,
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = Color(0xFF93BFFF),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

private fun previewDeviceItem(
    id: String,
    name: String,
    model: String,
    manufacturer: String,
    status: String,
    visitorId: String,
    lastActivity: String
): ActiveDeviceItem {
    return ActiveDeviceItem(
        id = id,
        status = status,
        visitorId = visitorId,
        lastActivity = lastActivity,
        createdAt = lastActivity,
        updatedAt = lastActivity,
        device = DeviceInfo(
            deviceId = id,
            deviceName = name,
            platform = "android",
            model = model,
            status = status,
            manufacturer = manufacturer,
            osVersion = "16"
        )
    )
}

@Preview(showBackground = true, backgroundColor = 0xFF0B101F, widthDp = 412, heightDp = 915, name = "Force Exit Screen Preview")
@Composable
private fun ForceExitScreenPreview() {
    var searchQuery by remember { mutableStateOf("") }
    var selectedDate by remember { mutableStateOf<String?>("2026-04-28") }
    val previewItems = remember {
        listOf(
            previewDeviceItem(
                id = "device-1",
                name = "e3q",
                model = "SM-S928B",
                manufacturer = "Samsung Galaxy",
                status = "inactive",
                visitorId = "Visitor-7",
                lastActivity = "2026-04-27T11:33:00Z"
            ),
            previewDeviceItem(
                id = "device-2",
                name = "k9",
                model = "Pixel 9 Pro",
                manufacturer = "Google",
                status = "active",
                visitorId = "Visitor-4",
                lastActivity = "2026-04-28T09:18:00Z"
            )
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(FxBgDark)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp)
        ) {
            ForceExitFilterHeader(
                searchQuery = searchQuery,
                onSearchQueryChange = { searchQuery = it },
                selectedDate = selectedDate,
                onDateClick = {},
                onClearDate = { selectedDate = null }
            )

            LazyColumn(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                items(previewItems, key = { it.id }) { item ->
                    ActiveDeviceCardItem(device = item, onClick = {})
                }
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0B101F, widthDp = 412, name = "Device Card Active")
@Composable
private fun ActiveDeviceCardPreviewActive() {
    Box(modifier = Modifier.padding(16.dp)) {
        ActiveDeviceCardItem(
            device = previewDeviceItem(
                id = "preview-active",
                name = "Alpha",
                model = "SM-S928B",
                manufacturer = "Samsung Galaxy",
                status = "active",
                visitorId = "Visitor-10",
                lastActivity = "2026-04-28T07:22:00Z"
            ),
            onClick = {}
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0B101F, widthDp = 412, name = "Device Card Inactive")
@Composable
private fun ActiveDeviceCardPreviewInactive() {
    Box(modifier = Modifier.padding(16.dp)) {
        ActiveDeviceCardItem(
            device = previewDeviceItem(
                id = "preview-inactive",
                name = "e3q",
                model = "SM-S928B",
                manufacturer = "Samsung Galaxy",
                status = "inactive",
                visitorId = "Visitor-7",
                lastActivity = "2026-04-27T11:33:00Z"
            ),
            onClick = {}
        )
    }
}

private val friendlyFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("dd MMM yyyy, h:mm a")

private fun formatDateFilterLabel(raw: String?): String {
    if (raw.isNullOrBlank()) return "Date"
    return runCatching {
        val selected = LocalDate.parse(raw, DateTimeFormatter.ISO_LOCAL_DATE)
        if (selected == LocalDate.now(ZoneOffset.UTC)) {
            "Today"
        } else {
            selected.format(DateTimeFormatter.ofPattern("dd MMM yyyy"))
        }
    }.getOrElse {
        raw
    }
}

private fun formatApiDate(utcTimeMillis: Long): String {
    return Instant.ofEpochMilli(utcTimeMillis)
        .atOffset(ZoneOffset.UTC)
        .toLocalDate()
        .format(DateTimeFormatter.ISO_LOCAL_DATE)
}

private fun parseApiDateToUtcMillis(raw: String): Long? = runCatching {
    LocalDate.parse(raw, DateTimeFormatter.ISO_LOCAL_DATE)
        .atStartOfDay()
        .toInstant(ZoneOffset.UTC)
        .toEpochMilli()
}.getOrNull()

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
