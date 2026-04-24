package com.sierra.admin.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sierra.admin.api.ApiService
import com.sierra.admin.modal.ApiResult
import com.sierra.admin.modal.ForceExitRequestItem
import com.sierra.admin.modal.ForceExitApproveResponse
import com.sierra.admin.modal.ForceExitDenyResponse
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class ExitRequestViewModel(app: Application) : AndroidViewModel(app) {
    private val api = ApiService(app)

    // State for pending requests
    private val _pendingRequestsState = MutableStateFlow<ApiResult<List<ForceExitRequestItem>>?>(null)
    val pendingRequestsState: StateFlow<ApiResult<List<ForceExitRequestItem>>?> = _pendingRequestsState

    // State for approve action
    private val _approveState = MutableStateFlow<ApiResult<ForceExitApproveResponse>?>(null)
    val approveState: StateFlow<ApiResult<ForceExitApproveResponse>?> = _approveState

    // State for deny action
    private val _denyState = MutableStateFlow<ApiResult<ForceExitDenyResponse>?>(null)
    val denyState: StateFlow<ApiResult<ForceExitDenyResponse>?> = _denyState

    // Loading states
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    fun loadPendingRequests(page: Int = 1, limit: Int = 20, facilityId: String = "") {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                when (val result = api.getPendingRequests(page, limit, facilityId)) {
                    is ApiResult.Success -> {
                        _pendingRequestsState.value = ApiResult.Success(result.data.items)
                    }
                    is ApiResult.Error -> {
                        _pendingRequestsState.value = result
                    }
                    is ApiResult.Loading -> {
                        _pendingRequestsState.value = result
                    }
                }
            } catch (e: Exception) {
                _pendingRequestsState.value = ApiResult.Error("Failed to load exit requests: ${e.message}")
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun approveRequest(requestId: String, adminNotes: String = "") {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                when (val result = api.approveRequest(requestId, adminNotes)) {
                    is ApiResult.Success -> {
                        _approveState.value = result
                        // Refresh the list after successful approval
                        loadPendingRequests()
                    }
                    is ApiResult.Error -> {
                        _approveState.value = result
                    }
                    is ApiResult.Loading -> {
                        _approveState.value = result
                    }
                }
            } catch (e: Exception) {
                _approveState.value = ApiResult.Error("Failed to approve request: ${e.message}")
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun denyRequest(requestId: String, adminNotes: String = "") {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                when (val result = api.denyRequest(requestId, adminNotes)) {
                    is ApiResult.Success -> {
                        _denyState.value = result
                        // Refresh the list after successful denial
                        loadPendingRequests()
                    }
                    is ApiResult.Error -> {
                        _denyState.value = result
                    }
                    is ApiResult.Loading -> {
                        _denyState.value = result
                    }
                }
            } catch (e: Exception) {
                _denyState.value = ApiResult.Error("Failed to deny request: ${e.message}")
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun clearStates() {
        _pendingRequestsState.value = null
        _approveState.value = null
        _denyState.value = null
    }
}
