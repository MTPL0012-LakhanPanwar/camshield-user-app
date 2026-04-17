package com.camshield.admin.viewmodel

import android.app.Application
import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sierra.admin.api.ApiService
import com.sierra.admin.modal.AdminData
import com.sierra.admin.modal.ApiResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class AdminViewModel(app: Application) : AndroidViewModel(app) {
    private val api = ApiService(app)

    private val _listState = MutableStateFlow<ApiResult<Unit>?>(null)
    val listState: StateFlow<ApiResult<Unit>?> = _listState

    val items = mutableStateListOf<AdminData>()
    var currentPage = 1; private set
    var totalPages = 1; private set
    var isLastPage = false; private set
    private var currentQuery = ""

    private val _detailState = MutableStateFlow<ApiResult<AdminData>?>(null)
    val detailState: StateFlow<ApiResult<AdminData>?> = _detailState

    fun loadAdmins(page: Int = 1, q: String = "", reset: Boolean = false) {
        viewModelScope.launch {
            if (reset || page == 1) {
                items.clear()
                currentPage = 1
                isLastPage = false
            }
            currentQuery = q
            _listState.value = ApiResult.Loading
            when (val result = api.getAdmins(page, q = q)) {
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
            loadAdmins(currentPage + 1, currentQuery)
        }
    }

    fun refreshAdmins() = loadAdmins(1, currentQuery, reset = true)

    fun loadDetail(idOrUsername: String) {
        viewModelScope.launch {
            _detailState.value = ApiResult.Loading
            _detailState.value = api.getAdminDetail(idOrUsername)
        }
    }
}
