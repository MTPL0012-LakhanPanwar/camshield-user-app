package com.camshield.admin.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sierra.admin.api.ApiService
import com.sierra.admin.auth.TokenManager
import com.sierra.admin.modal.ApiResult
import com.sierra.admin.modal.AuthResponse
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class AuthViewModel(app: Application) : AndroidViewModel(app) {
    private val api = ApiService(app)
    val tokenManager = TokenManager(app)

    private val _loginState = MutableStateFlow<ApiResult<AuthResponse>?>(null)
    val loginState: StateFlow<ApiResult<AuthResponse>?> = _loginState

    private val _registerState = MutableStateFlow<ApiResult<AuthResponse>?>(null)
    val registerState: StateFlow<ApiResult<AuthResponse>?> = _registerState

    fun login(username: String, password: String) {
        viewModelScope.launch {
            _loginState.value = ApiResult.Loading
            _loginState.value = api.login(username.trim(), password)
        }
    }

    fun register(username: String, password: String) {
        viewModelScope.launch {
            _registerState.value = ApiResult.Loading
            _registerState.value = api.register(username.trim(), password)
        }
    }

    fun logout() = tokenManager.clearAll()
    fun resetLogin() { _loginState.value = null }
    fun resetRegister() { _registerState.value = null }
}
