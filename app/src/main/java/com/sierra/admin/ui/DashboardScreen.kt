package com.camshield.admin.ui

import android.content.Intent
import androidx.activity.result.ActivityResultLauncher
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.People
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.camshield.admin.viewmodel.AdminViewModel
import com.camshield.admin.viewmodel.DeviceViewModel
import com.camshield.admin.viewmodel.FacilityViewModel
import com.sierra.admin.ui.AdminListContent
import kotlinx.coroutines.launch

private val DsBgDark = Color(0xFF0B101F)
private val DsNavBg = Color(0xFF111727)
private val DsAccentBlue = Color(0xFF2196F3)
private val DsTextGray = Color(0xFF8A92A6)

sealed class DashboardTab(val route: String, val icon: ImageVector, val title: String) {
    object Facilities : DashboardTab("tab_facilities", Icons.Default.Business, "Facilities")
    object Devices : DashboardTab("tab_devices", Icons.Default.ExitToApp, "Active Devices")
    object Admins : DashboardTab("tab_admins", Icons.Default.People, "Exit Requests")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    outerNavController: NavHostController,
    facilityViewModel: FacilityViewModel,
    adminViewModel: AdminViewModel,
    deviceViewModel: DeviceViewModel,
    facilityDetailLauncher: ActivityResultLauncher<Intent>,
    createUpdateLauncher: ActivityResultLauncher<Intent>,
    onLogout: () -> Unit
) {
    val tabNavController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val showSnackbar: (String) -> Unit = { msg ->
        scope.launch { snackbarHostState.showSnackbar(msg) }
    }
    val handleUnauthorized: () -> Unit = { onLogout() }

    Scaffold(
        containerColor = DsBgDark,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        "ADMIN PORTAL",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp
                    )
                },
                actions = {
                    TextButton(onClick = onLogout) {
                        Icon(Icons.Default.Logout, contentDescription = null, tint = DsAccentBlue, modifier = Modifier.size(18.dp))
                        Text("Logout", color = DsAccentBlue, fontSize = 16.sp, modifier = Modifier.padding(start = 6.dp))
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = DsBgDark)
            )
        },
        bottomBar = {
            val tabs = listOf(DashboardTab.Facilities, DashboardTab.Devices, DashboardTab.Admins)
            val navBackStackEntry by tabNavController.currentBackStackEntryAsState()
            val currentRoute = navBackStackEntry?.destination?.route
            NavigationBar(containerColor = DsNavBg) {
                tabs.forEach { tab ->
                    val selected = currentRoute == tab.route
                    NavigationBarItem(
                        icon = {
                            Icon(tab.icon, tab.title, modifier = Modifier.size(24.dp))
                        },
                        label = {
                            Text(
                                tab.title,
                                fontSize = 11.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        selected = selected,
                        onClick = {
                            if (currentRoute != tab.route) {
                                tabNavController.navigate(tab.route) {
                                    popUpTo(tabNavController.graph.startDestinationId) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = DsAccentBlue,
                            selectedTextColor = DsAccentBlue,
                            unselectedIconColor = DsTextGray,
                            unselectedTextColor = DsTextGray,
                            indicatorColor = Color.Transparent
                        )
                    )
                }
            }
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
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            NavHost(
                navController = tabNavController,
                startDestination = DashboardTab.Facilities.route
            ) {
                composable(DashboardTab.Facilities.route) {
                    FacilityContent(
                        viewModel = facilityViewModel,
                        showSnackbar = showSnackbar,
                        onUnauthorized = handleUnauthorized,
                        facilityDetailLauncher = facilityDetailLauncher,
                        createUpdateLauncher = createUpdateLauncher
                    )
                }
                composable(DashboardTab.Devices.route) {
                    ForceExitContent(
                        navController = outerNavController,
                        viewModel = deviceViewModel,
                        onUnauthorized = handleUnauthorized
                    )
                }
                composable(DashboardTab.Admins.route) {
                    AdminListContent(
                        viewModel = adminViewModel,
                        onUnauthorized = handleUnauthorized
                    )
                }
            }
        }
    }
}
