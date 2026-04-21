package com.camshield.admin.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sierra.admin.api.ApiService
import com.sierra.admin.modal.ActiveDeviceItem
import com.sierra.admin.modal.ApiResult
import com.sierra.admin.modal.EnrollmentDetail
import com.sierra.admin.modal.ForceExitResponse
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class DeviceViewModel(app: Application) : AndroidViewModel(app) {
    private val api = ApiService(app)

    private val _listState = MutableStateFlow<ApiResult<Unit>?>(null)
    val listState: StateFlow<ApiResult<Unit>?> = _listState

    val items = mutableListOf<ActiveDeviceItem>()
    var currentPage = 1; private set
    var totalPages = 1; private set
    var isLastPage = false; private set
    private var currentQuery = ""

    private val _enrollmentState = MutableStateFlow<ApiResult<EnrollmentDetail>?>(null)
    val enrollmentState: StateFlow<ApiResult<EnrollmentDetail>?> = _enrollmentState

    private val _forceExitState = MutableStateFlow<ApiResult<ForceExitResponse>?>(null)
    val forceExitState: StateFlow<ApiResult<ForceExitResponse>?> = _forceExitState

    private val _selectedDevice = MutableStateFlow<ActiveDeviceItem?>(null)
    val selectedDevice: StateFlow<ActiveDeviceItem?> = _selectedDevice

    fun loadDevices(page: Int = 1, q: String = "", reset: Boolean = false) {
        viewModelScope.launch {
            if (reset || page == 1) {
                items.clear()
                currentPage = 1
                isLastPage = false
            }
            currentQuery = q
            _listState.value = ApiResult.Loading
            when (val result = api.getActiveDevices(page, q = q)) {
                is ApiResult.Success -> {
                    currentPage = result.data.page
                    totalPages = result.data.totalPages
                    isLastPage = currentPage >= totalPages
                    items.addAll(result.data.items)
                    _listState.value = ApiResult.Success(Unit)
                }
                is ApiResult.Error -> _listState.value = result
                else -> {}
            }
        }
    }

    fun loadNextPage() {
        if (!isLastPage && _listState.value !is ApiResult.Loading) {
            loadDevices(currentPage + 1, currentQuery)
        }
    }

    fun refreshDevices() = loadDevices(1, currentQuery, reset = true)

    fun selectDevice(device: ActiveDeviceItem) { _selectedDevice.value = device }
    fun clearSelectedDevice() { _selectedDevice.value = null }

    fun loadEnrollment(deviceId: String) {
        viewModelScope.launch {
            _enrollmentState.value = ApiResult.Loading
            _enrollmentState.value = api.getActiveEnrollment(deviceId)
        }
    }

    fun forceExit(deviceId: String, reason: String, initiatedBy: String) {
        viewModelScope.launch {
            _forceExitState.value = ApiResult.Loading
            _forceExitState.value = api.forceExit(deviceId, reason, initiatedBy)
        }
    }

    fun resetForceExit() { _forceExitState.value = null }
    fun resetEnrollment() { _enrollmentState.value = null }
}
