package com.camshield.admin.viewmodel

import android.app.Application
import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sierra.admin.api.ApiService
import com.sierra.admin.modal.ApiResult
import com.sierra.admin.modal.FacilityData
import com.sierra.admin.modal.QRPair
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class FacilityViewModel(app: Application) : AndroidViewModel(app) {
    private val api = ApiService(app)

    // List state (accumulated for infinite scroll)
    private val _listState = MutableStateFlow<ApiResult<Unit>?>(null)
    val listState: StateFlow<ApiResult<Unit>?> = _listState

    val items = mutableStateListOf<FacilityData>()
    var currentPage = 1; private set
    var totalPages = 1; private set
    var isLastPage = false; private set
    private var currentQuery = ""
    private var currentStatus = ""

    // Detail state (for pre-filling edit form)
    private val _detailState = MutableStateFlow<ApiResult<FacilityData>?>(null)
    val detailState: StateFlow<ApiResult<FacilityData>?> = _detailState

    // Save state (create/update result)
    private val _saveState = MutableStateFlow<ApiResult<String>?>(null)
    val saveState: StateFlow<ApiResult<String>?> = _saveState

    // Delete state
    private val _deleteState = MutableStateFlow<ApiResult<String>?>(null)
    val deleteState: StateFlow<ApiResult<String>?> = _deleteState

    // QR codes state
    private val _qrState = MutableStateFlow<ApiResult<QRPair>?>(null)
    val qrState: StateFlow<ApiResult<QRPair>?> = _qrState

    fun loadFacilities(page: Int = 1, q: String = "", status: String = "", reset: Boolean = false) {
        viewModelScope.launch {
            if (reset || page == 1) {
                items.clear()
                currentPage = 1
                isLastPage = false
            }
            currentQuery = q
            currentStatus = status
            _listState.value = ApiResult.Loading
            when (val result = api.getFacilities(page, q = q, status = status)) {
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
            loadFacilities(currentPage + 1, currentQuery, currentStatus)
        }
    }

    fun refreshFacilities() = loadFacilities(1, currentQuery, currentStatus, reset = true)

    fun loadDetail(id: String) {
        viewModelScope.launch {
            _detailState.value = ApiResult.Loading
            _detailState.value = api.getFacilityDetail(id)
        }
    }

    fun createFacility(
        name: String, description: String,
        address: String, city: String, state: String, country: String,
        emails: List<String>, timezone: String, status: String
    ) {
        viewModelScope.launch {
            _saveState.value = ApiResult.Loading
            _saveState.value = when (val r = api.createFacility(name, description, address, city, state, country, emails, timezone, status)) {
                is ApiResult.Success -> ApiResult.Success("Facility created. Today's QR codes generated.")
                is ApiResult.Error -> r
                else -> null
            }
        }
    }

    fun updateFacility(
        id: String, name: String, description: String,
        address: String, city: String, state: String, country: String,
        emails: List<String>, timezone: String, status: String
    ) {
        viewModelScope.launch {
            _saveState.value = ApiResult.Loading
            _saveState.value = when (val r = api.updateFacility(id, name, description, address, city, state, country, emails, timezone, status)) {
                is ApiResult.Success -> ApiResult.Success("Facility updated successfully")
                is ApiResult.Error -> r
                else -> null
            }
        }
    }

    fun deleteFacility(id: String) {
        viewModelScope.launch {
            _deleteState.value = ApiResult.Loading
            _deleteState.value = when (val r = api.deleteFacility(id)) {
                is ApiResult.Success -> ApiResult.Success("Facility deleted successfully")
                is ApiResult.Error -> r
                else -> null
            }
        }
    }

    fun loadQRCodes(facilityId: String) {
        viewModelScope.launch {
            _qrState.value = ApiResult.Loading
            _qrState.value = api.getFacilityQRCodes(facilityId)
        }
    }

    fun resetSave() { _saveState.value = null }
    fun resetDelete() { _deleteState.value = null }
    fun resetDetail() { _detailState.value = null }
    fun resetQR() { _qrState.value = null }
}
