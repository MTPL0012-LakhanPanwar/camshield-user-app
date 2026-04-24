package com.sierra.admin.activity

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.camshield.admin.ui.theme.CameraLockFacilityTheme
import com.sierra.admin.modal.ForceExitRequestItem
import com.sierra.admin.modal.ApiResult
import com.sierra.admin.viewmodel.ExitRequestViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

private val DetailBgDark = Color(0xFF0B101F)
private val DetailCardBg = Color(0xFF111727)
private val DetailAccentBlue = Color(0xFF2196F3)
private val DetailTextGray = Color(0xFF8A92A6)

@Composable
private fun DetailItem(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "$label :-",
            fontSize = 14.sp,
            color = DetailTextGray,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = value,
            fontSize = 14.sp,
            color = Color.White,
            fontWeight = FontWeight.Normal
        )
    }
}

private fun formatDateTime(isoDate: String): String {
    return try {
        val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault())
        inputFormat.timeZone = TimeZone.getTimeZone("UTC")
        val date = inputFormat.parse(isoDate)
        
        val outputFormat = SimpleDateFormat("dd/MM/yyyy hh:mm a", Locale.getDefault())
        outputFormat.timeZone = TimeZone.getDefault()
        date?.let { outputFormat.format(it) } ?: isoDate
    } catch (e: Exception) {
        isoDate
    }
}

class ExitRequestDetailActivity : ComponentActivity() {

    private val viewModel: ExitRequestViewModel by viewModels()

    companion object {
        private const val EXTRA_REQUEST_DATA = "request_data"
        
        fun createIntent(context: android.content.Context, request: ForceExitRequestItem): Intent {
            return Intent(context, ExitRequestDetailActivity::class.java).apply {
                putExtra(EXTRA_REQUEST_DATA, request)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val request = intent.getSerializableExtra(EXTRA_REQUEST_DATA) as? ForceExitRequestItem
        Log.e(javaClass.name, "onCreate: $request")
        if (request == null) {
            finish()
            return
        }

        setContent {
            CameraLockFacilityTheme {
                ExitRequestDetailScreen(
                    request = request,
                    viewModel = viewModel,
                    onBackPressed = { finish() }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExitRequestDetailScreen(
    request: ForceExitRequestItem,
    viewModel: ExitRequestViewModel,
    onBackPressed: () -> Unit
) {
    val approveState by viewModel.approveState.collectAsStateWithLifecycle()
    val denyState by viewModel.denyState.collectAsStateWithLifecycle()
    val isLoading = approveState is ApiResult.Loading || denyState is ApiResult.Loading
    val scope = rememberCoroutineScope()

    LaunchedEffect(approveState) {
        if (approveState is ApiResult.Success) {
            onBackPressed()
        }
    }

    LaunchedEffect(denyState) {
        if (denyState is ApiResult.Success) {
            onBackPressed()
        }
    }

    Scaffold(
        containerColor = DetailBgDark,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        "EXIT REQUEST DETAILS",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp,
                        color = Color.White
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackPressed) {
                        Icon(Icons.Default.ArrowBack, null, tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = DetailBgDark)
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
        ) {
            Spacer(modifier = Modifier.height(24.dp))

            // Scrollable Content
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                // Request Details Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = DetailCardBg),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        // Status
                        DetailItem("Status", request.status.replaceFirstChar { it.uppercase() })

                        Spacer(modifier = Modifier.height(16.dp))

                        // Device Information
                        Text(
                            text = "Device Information",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = DetailAccentBlue,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )

                        DetailItem(
                            "Device Name",
                            request.device.deviceInfo.deviceName.ifBlank { "Unknown Device" })
                        DetailItem("Manufacturer", request.device.deviceInfo.manufacturer)
                        DetailItem("OS Version", request.device.deviceInfo.osVersion)
                        DetailItem("Platform", request.device.deviceInfo.platform)

                        Spacer(modifier = Modifier.height(16.dp))

                        // Visitor Information
                        Text(
                            text = "Visitor Information",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = DetailAccentBlue,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )

                        DetailItem("Visitor ID", request.visitorId ?: "N/A")

                        Spacer(modifier = Modifier.height(16.dp))

                        // Facility Information
                        Text(
                            text = "Facility Information",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = DetailAccentBlue,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )

                        DetailItem(
                            "Facility Name",
                            request.facility.name.ifBlank { "Unknown Facility" })
                        DetailItem("Facility Location", request.facility.location?.let {
                            "${it.address}, ${it.city}, ${it.state}".trim().ifBlank { "N/A" }
                        } ?: "N/A")

                        Spacer(modifier = Modifier.height(16.dp))

                        // Request Details
                        Text(
                            text = "Request Details",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = DetailAccentBlue,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )

                        request.reason?.let { reason ->
                            DetailItem("Reason", reason)
                        }

                        request.requestedAt?.let { requestedAt ->
                            DetailItem("Requested At", formatDateTime(requestedAt))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Action Buttons - Always visible at bottom
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Button(
                    onClick = {
                        scope.launch {
                            viewModel.denyRequest(request.requestId)
                        }
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFF44336), // Red
                        contentColor = Color.White
                    ),
                    enabled = !isLoading
                ) {
                    if (denyState is ApiResult.Loading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = Color.White
                        )
                    } else {
                        Text("Deny", fontSize = 16.sp)
                    }
                }

                Button(
                    onClick = {
                        scope.launch {
                            viewModel.approveRequest(request.requestId)
                        }
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = DetailAccentBlue,
                        contentColor = Color.White
                    ),
                    enabled = !isLoading
                ) {
                    if (approveState is ApiResult.Loading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = Color.White
                        )
                    } else {
                        Text("Approve", fontSize = 16.sp)
                    }
                }
            }
        }
    }
}