package com.sierra.admin.activity

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.camshield.admin.ui.AdminSplashScreen
import com.camshield.admin.ui.DashboardScreen
import com.camshield.admin.ui.DeviceDetailScreen
import com.camshield.admin.ui.theme.CameraLockFacilityTheme
import com.camshield.admin.viewmodel.AdminViewModel
import com.camshield.admin.viewmodel.AuthViewModel
import com.camshield.admin.viewmodel.DeviceViewModel
import com.camshield.admin.viewmodel.FacilityViewModel
import com.sierra.admin.auth.TokenManager
import kotlinx.coroutines.delay
import kotlin.jvm.java

class MainActivity : ComponentActivity() {

    private val authViewModel: AuthViewModel by viewModels()
    private val facilityViewModel: FacilityViewModel by viewModels()
    private val adminViewModel: AdminViewModel by viewModels()
    private val deviceViewModel: DeviceViewModel by viewModels()

    private lateinit var facilityDetailLauncher: ActivityResultLauncher<Intent>
    private lateinit var createUpdateLauncher: ActivityResultLauncher<Intent>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Launcher for CreateUpdateFacility — on save, refresh the list
        createUpdateLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == CreateUpdateFacility.RESULT_SAVED) {
                facilityViewModel.refreshFacilities()
            }
        }

        // Launcher for FacilityDetailActivity
        facilityDetailLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            when (result.resultCode) {
                FacilityDetailActivity.RESULT_EDIT -> {
                    // Launch CreateUpdateFacility for edit
                    val editId = result.data?.getStringExtra(
                        FacilityDetailActivity.EXTRA_EDIT_FACILITY_ID
                    )
                    if (editId != null) {
                        val intent = Intent(this, CreateUpdateFacility::class.java)
                        intent.putExtra(CreateUpdateFacility.EXTRA_FACILITY_ID, editId)
                        createUpdateLauncher.launch(intent)
                    }
                }
                FacilityDetailActivity.RESULT_DELETED -> {
                    facilityViewModel.refreshFacilities()
                }
            }
        }

        val tokenManager = TokenManager(this)

        // Check if launched with a specific destination (from Login/Register activities)
        val requestedDestination = intent.getStringExtra("destination")
        val startDest = if (requestedDestination == "dashboard") "dashboard" else "splash"

        setContent {
            CameraLockFacilityTheme {
                val navController = rememberNavController()

                NavHost(navController = navController, startDestination = startDest) {

                    composable("splash") {
                        LaunchedEffect(Unit) {
                            delay(1500)
                            if (tokenManager.isLoggedIn()) {
                                navController.navigate("dashboard") {
                                    popUpTo("splash") { inclusive = true }
                                }
                            } else {
                                val loginIntent = Intent(this@MainActivity, LoginActivity::class.java)
                                loginIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                                startActivity(loginIntent)
                                finish()
                            }
                        }
                        AdminSplashScreen()
                    }

                    composable("dashboard") {
                        DashboardScreen(
                            outerNavController = navController,
                            facilityViewModel = facilityViewModel,
                            adminViewModel = adminViewModel,
                            deviceViewModel = deviceViewModel,
                            facilityDetailLauncher = facilityDetailLauncher,
                            createUpdateLauncher = createUpdateLauncher,
                            onLogout = {
                                authViewModel.logout()
                                val loginIntent = Intent(this@MainActivity, LoginActivity::class.java)
                                loginIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                                startActivity(loginIntent)
                                finish()
                            }
                        )
                    }

                    composable("device/{deviceId}") { backStack ->
                        val deviceId = backStack.arguments?.getString("deviceId")
                            ?: return@composable
                        DeviceDetailScreen(
                            deviceId = deviceId,
                            navController = navController,
                            viewModel = deviceViewModel,
                            tokenManager = tokenManager,
                            onUnauthorized = {
                                authViewModel.logout()
                                val loginIntent = Intent(this@MainActivity, LoginActivity::class.java)
                                loginIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                                startActivity(loginIntent)
                                finish()
                            }
                        )
                    }
                }
            }
        }
    }
}
