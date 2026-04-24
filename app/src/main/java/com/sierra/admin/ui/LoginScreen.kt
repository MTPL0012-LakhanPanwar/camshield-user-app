package com.camshield.admin.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.camshield.admin.viewmodel.AuthViewModel
import com.sierra.admin.modal.ApiResult
import com.sierra.camblock.R
import kotlinx.coroutines.launch

private val BgDark = Color(0xFF0B101F)
private val CardBg = Color(0xFF161C2C)
private val AccentBlue = Color(0xFF2196F3)
private val TextGray = Color(0xFF8A92A6)

@Composable
fun LoginScreen(
    viewModel: AuthViewModel,
    onLoginSuccess: () -> Unit,
    onNavigateToRegister: () -> Unit
) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    val loginState by viewModel.loginState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val isLoading = loginState is ApiResult.Loading

    LaunchedEffect(loginState) {
        when (val state = loginState) {
            is ApiResult.Success -> {
                viewModel.resetLogin()
                onLoginSuccess()
            }
            is ApiResult.Error -> {
                scope.launch { snackbarHostState.showSnackbar(state.message) }
                viewModel.resetLogin()
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
            // Logo + Title
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
                    Text("Sign In", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)

                    AuthTextField(
                        label = "Username",
                        value = username,
                        onValueChange = { username = it },
                        enabled = !isLoading
                    )

                    AuthTextField(
                        label = "Password",
                        value = password,
                        onValueChange = { password = it },
                        enabled = !isLoading,
                        isPassword = true,
                        passwordVisible = passwordVisible,
                        onTogglePassword = { passwordVisible = !passwordVisible }
                    )

                    Button(
                        onClick = {
                            if (username.isBlank() || password.isBlank()) {
                                scope.launch { snackbarHostState.showSnackbar("Username and password are required") }
                            } else {
                                viewModel.login(username, password)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth(0.60f)
                            .align(Alignment.CenterHorizontally)
                            .height(52.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AccentBlue),
                        enabled = !isLoading
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Text("Sign In", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            // Spacer(Modifier.height(24.dp))
            // Row(horizontalArrangement = Arrangement.Center) {
            //     Text("Don't have an account? ", color = TextGray, fontSize = 14.sp)
            //     Text(
            //         "Register",
            //         color = AccentBlue,
            //         fontSize = 14.sp,
            //         fontWeight = FontWeight.SemiBold,
            //         modifier = Modifier.clickable {
            //             onNavigateToRegister()
            //         }
            //     )
            // }
        }
    }
}

@Composable
fun AuthTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean = true,
    isPassword: Boolean = false,
    passwordVisible: Boolean = false,
    onTogglePassword: (() -> Unit)? = null
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, color = TextGray, fontSize = 13.sp)
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled,
            singleLine = true,
            visualTransformation = if (isPassword && !passwordVisible) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = if (isPassword) KeyboardOptions(keyboardType = KeyboardType.Password) else KeyboardOptions.Default,
            trailingIcon = if (isPassword && onTogglePassword != null) {
                {
                    IconButton(onClick = onTogglePassword) {
                        Icon(
                            imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = null,
                            tint = TextGray
                        )
                    }
                }
            } else null,
            shape = RoundedCornerShape(10.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedContainerColor = Color(0xFF0D1426),
                unfocusedContainerColor = Color(0xFF0D1426),
                focusedBorderColor = AccentBlue,
                unfocusedBorderColor = Color(0xFF2A3245),
                cursorColor = AccentBlue
            )
        )
    }
}
