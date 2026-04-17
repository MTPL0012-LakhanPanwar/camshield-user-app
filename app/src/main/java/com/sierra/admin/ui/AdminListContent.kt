package com.sierra.admin.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.camshield.admin.viewmodel.AdminViewModel
import com.sierra.admin.modal.AdminData
import com.sierra.admin.modal.ApiResult
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val BgDark = Color(0xFF0B101F)
private val CardBg = Color(0xFF161C2C)
private val AccentBlue = Color(0xFF2196F3)
private val TextGray = Color(0xFF8A92A6)
private val DividerColor = Color(0xFF2A3245)

@OptIn(FlowPreview::class, ExperimentalMaterialApi::class)
@Composable
fun AdminListContent(
    viewModel: AdminViewModel,
    onUnauthorized: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val listState by viewModel.listState.collectAsState()
    val lazyListState = rememberLazyListState()
    val isLoading = listState is ApiResult.Loading
    val isRefreshing = isLoading && viewModel.items.isNotEmpty()
    val pullRefreshState = rememberPullRefreshState(
        refreshing = isRefreshing,
        onRefresh = { viewModel.refreshAdmins() }
    )

    val searchFlow = remember { MutableStateFlow("") }
    LaunchedEffect(Unit) {
        searchFlow.drop(1).debounce(400).collect { q ->
            viewModel.loadAdmins(1, q, reset = true)
        }
    }
    LaunchedEffect(searchQuery) { searchFlow.value = searchQuery }

    LaunchedEffect(Unit) {
        if (viewModel.items.isEmpty()) viewModel.loadAdmins(reset = true)
    }

    LaunchedEffect(listState) {
        if (listState is ApiResult.Error && (listState as ApiResult.Error).code == 401) {
            onUnauthorized()
        }
    }

    // Infinite scroll
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
            .background(BgDark)
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
                    placeholder = { Text("Search admins...", color = TextGray) },
                    leadingIcon = { Icon(Icons.Default.Search, null, tint = TextGray) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedContainerColor = CardBg,
                        unfocusedContainerColor = CardBg,
                        focusedBorderColor = AccentBlue,
                        unfocusedBorderColor = Color.Transparent
                    )
                )
            }

        when {
            isLoading && viewModel.items.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = AccentBlue)
                }
            }
            listState is ApiResult.Error && viewModel.items.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        val err = listState as ApiResult.Error
                        Text(
                            if (err.code == 404) "There are no admins available right now." else err.message.ifBlank { "Couldn’t load admins. Pull to refresh or try again." },
                            color = Color(0xFFEF5350),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = { viewModel.refreshAdmins() }, colors = ButtonDefaults.buttonColors(containerColor = AccentBlue)) {
                            Text("Retry")
                        }
                    }
                }
            }
            viewModel.items.isEmpty() && !isLoading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("There are no admins available right now", color = TextGray)
                }
            }
            else -> {
                LazyColumn(state = lazyListState, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(viewModel.items) { admin ->
                        AdminCard(admin)
                    }
                    if (isLoading) {
                        item {
                            Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = AccentBlue, modifier = Modifier.size(24.dp))
                            }
                        }
                    }
                    if (viewModel.isLastPage && viewModel.items.isNotEmpty()) {
                        item {
                            Box(Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) {
                                Text("All ${viewModel.items.size} admins loaded", color = TextGray, fontSize = 12.sp)
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
            contentColor = AccentBlue,
            backgroundColor = CardBg
        )
    }
}

@Composable
private fun AdminCard(admin: AdminData) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(Color(0xFF1E61EB).copy(alpha = 0.2f), RoundedCornerShape(22.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Person, null, tint = AccentBlue, modifier = Modifier.size(24.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(admin.username, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                if (!admin.createdAt.isNullOrBlank()) {
                    Text(
                        "Joined: ${formatDateTimeFriendly2(admin.createdAt)}",
                        color = TextGray,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
            val roleLabel = admin.role?.takeIf { it.isNotBlank() } ?: "Admin"
            Text( 
                text = "Role: $roleLabel",
                color = TextGray,
                fontSize = 11.sp
            )
        }
    }
}
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
