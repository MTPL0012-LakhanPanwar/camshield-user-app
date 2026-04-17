package com.camshield.admin.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.camshield.admin.viewmodel.AuthViewModel
import com.sierra.admin.modal.ApiResult
import com.sierra.camblock.R
import kotlinx.coroutines.launch

@Composable
fun RegisterScreen(
    viewModel: AuthViewModel,
    onRegisterSuccess: () -> Unit,
    onNavigateBack: () -> Unit
) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    val registerState by viewModel.registerState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val isLoading = registerState is ApiResult.Loading
    val allowedCharsRegex = "^[a-zA-Z0-9!@#\$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>\\/?]*\$".toRegex()
    val BgDark = Color(0xFF0B101F)
    val CardBg = Color(0xFF161C2C)
    val AccentBlue = Color(0xFF2196F3)
    val TextGray = Color(0xFF8A92A6)

    LaunchedEffect(registerState) {
        when (val state = registerState) {
            is ApiResult.Success -> {
                viewModel.resetRegister()
                onRegisterSuccess()
            }
            is ApiResult.Error -> {
                scope.launch { snackbarHostState.showSnackbar(state.message) }
                viewModel.resetRegister()
            }
            else -> {}
        }
    }

    Scaffold(
        containerColor = BgDark,
        snackbarHost = {
            SnackbarHost(snackbarHostState) { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = Color(0xFFEF5350),
                    contentColor = Color.White
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .shadow(12.dp, CircleShape)
                    .background(CardBg, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.admin),
                    contentDescription = "App logo",
                    modifier = Modifier.size(60.dp)
                )
            }
            Spacer(Modifier.height(16.dp))
            Text(
                "SECURE ZONE",
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp
            )
            Text(
                "ADMIN PORTAL",
                color = AccentBlue,
                fontSize = 14.sp,
                letterSpacing = 4.sp,
                modifier = Modifier.padding(top = 4.dp, bottom = 32.dp)
            )

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = CardBg),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text("Create Admin Account", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)

                    AuthTextField(
                        label = "Username",
                        value = username,
                        onValueChange = {
                            if (it.length <= 30 && it.matches(allowedCharsRegex)) {
                                username = it
                            }
                        },
                        enabled = !isLoading
                    )

                    AuthTextField(
                        label = "Password",
                        value = password,
                        onValueChange = {
                            if (it.length <= 12 && it.matches(allowedCharsRegex)) {
                                password = it
                            }
                        },
                        enabled = !isLoading,
                        isPassword = true,
                        passwordVisible = passwordVisible,
                        onTogglePassword = { passwordVisible = !passwordVisible }
                    )

                    Button(
                        onClick = {
                            when {
                                username.isBlank() || password.isBlank() ->
                                    scope.launch { snackbarHostState.showSnackbar("Username and password are required") }

                                // Specific Password Length Check (10 to 12)
                                password.length < 10 ->
                                    scope.launch { snackbarHostState.showSnackbar("Password must be between 10 and 12 characters") }

                                else -> viewModel.register(username, password)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth(0.65f)
                            .align(Alignment.CenterHorizontally)
                            .height(52.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AccentBlue),
                        enabled = !isLoading
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Text("Create Account", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.Center) {
                Text("Already have an account? ", color = TextGray, fontSize = 14.sp)
                Text(
                    "Sign In",
                    color = AccentBlue,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable { onNavigateBack() }
                )
            }
        }
    }
}
