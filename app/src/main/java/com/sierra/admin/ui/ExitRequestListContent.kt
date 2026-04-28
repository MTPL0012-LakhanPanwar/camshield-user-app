package com.sierra.admin.ui

import android.content.Intent
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Card
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.debounce
import com.sierra.admin.modal.ForceExitRequestItem
import com.sierra.admin.modal.ApiResult
import com.sierra.admin.viewmodel.ExitRequestViewModel
import com.sierra.admin.activity.ExitRequestDetailActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.drop

private val DsBgDark = Color(0xFF0B101F)
private val DsNavBg = Color(0xFF111727)
private val DsAccentBlue = Color(0xFF2196F3)
private val DsTextGray = Color(0xFF8A92A6)

@OptIn(FlowPreview::class, ExperimentalMaterialApi::class)
@Composable
fun ExitRequestListContent(
    viewModel: ExitRequestViewModel
) {
    var searchQuery by remember { mutableStateOf("") }
    val pendingRequestsState by viewModel.pendingRequestsState.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()

    // Activity result launcher to refresh list when detail activity returns
    val detailLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        // Refresh the list when returning from detail activity
        viewModel.loadPendingRequests()
    }

    // Search debounce - drop(1) skips the initial StateFlow value to avoid
    // a duplicate load race with explicit initial-load LaunchedEffect below.
    val searchFlow = remember { MutableStateFlow("") }
    LaunchedEffect(Unit) {
        viewModel.loadPendingRequests()
    }
    LaunchedEffect(searchQuery) { searchFlow.value = searchQuery }

    LaunchedEffect(Unit) {
        searchFlow.drop(1).debounce(400).collect { q ->
            viewModel.loadPendingRequests()
        }
    }
    // Extract data from state
    val allExitRequests = when (val state = pendingRequestsState) {
        is ApiResult.Success -> state.data
        is ApiResult.Error -> emptyList()
        is ApiResult.Loading -> emptyList()
        null -> emptyList()
    }
    
    // Filter requests based on search query
    val exitRequests = if (searchQuery.isBlank()) {
        allExitRequests
    } else {
        allExitRequests.filter { request ->
            request.device.deviceInfo.deviceName.contains(searchQuery, ignoreCase = true) ||
            request.visitorId?.contains(searchQuery, ignoreCase = true) == true ||
            request.facility.name.contains(searchQuery, ignoreCase = true)
        }
    }
    
    val errorMessage = when (val state = pendingRequestsState) {
        is ApiResult.Error -> state.message
        else -> null
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DsBgDark)
            .padding(16.dp)
    ) {
        // Search Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp),
                placeholder = { Text("Find Device Name or Visitor ID",
                    color = DsTextGray,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                ) },
                leadingIcon = { 
                    Icon(
                        Icons.Default.Search, 
                        contentDescription = "Search",
                        tint = DsTextGray
                    ) 
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedContainerColor = DsNavBg,
                    unfocusedContainerColor = DsNavBg,
                    focusedBorderColor = DsAccentBlue,
                    unfocusedBorderColor = DsTextGray.copy(alpha = 0.2f)
                )
            )

            Box(
                modifier = Modifier
                    .size(56.dp)
                    .border(
                        width = 1.dp,
                        color = DsTextGray.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(16.dp)
                    )
                    .background(DsNavBg, RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center
            ) {
                IconButton(
                    onClick = {
                        viewModel.loadPendingRequests()
                    },
                    enabled = !isLoading,
                    modifier = Modifier.size(56.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh",
                        tint = Color.White
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Title
        Text(
            text = "Pending Exit Requests",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Loading indicator
        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }

        // Error message
        errorMessage?.let { error ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                backgroundColor = Color.Red.copy(alpha = 0.1f)
            ) {
                Text(
                    text = error,
                    modifier = Modifier.padding(16.dp),
                    color = Color.Red
                )
            }
        }

        // Exit requests list
        if (!isLoading && errorMessage == null) {
            if (exitRequests.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No pending exit requests",
                        color = Color.Gray,
                        fontSize = 16.sp
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(exitRequests) { request ->
                        ExitRequestItemCard(request = request, detailLauncher = detailLauncher)
                    }
                }
            }
        }
    }
}

@Composable
private fun ExitRequestItemCard(
    request: ForceExitRequestItem,
    detailLauncher: androidx.activity.result.ActivityResultLauncher<android.content.Intent>
) {
    val context = LocalContext.current
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                val intent = ExitRequestDetailActivity.createIntent(context, request)
                detailLauncher.launch(intent)
            },
        colors = CardDefaults.cardColors(containerColor = DsNavBg)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Status (horizontal layout)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = request.status.replaceFirstChar { it.uppercase() },
                    color = when (request.status) {
                        "pending" -> DsAccentBlue
                        "approved" -> Color(0xFF4CAF50) // Green
                        "denied" -> Color(0xFFF44336) // Red
                        else -> DsTextGray
                    },
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Device Name
            Text(
                text = "DeviceName :- ${request.device.deviceInfo.deviceName.ifBlank { "Unknown Device" }}",
                color = Color.White,
                fontSize = 14.sp,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Visitor ID
            Text(
                text = "VisitorID :- ${request.visitorId ?: "N/A"}",
                color = Color.White,
                fontSize = 14.sp,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Facility Name
            Text(
                text = "FacilityName :- ${request.facility.name.ifBlank { "Unknown Facility" }}",
                color = Color.White,
                fontSize = 14.sp,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}