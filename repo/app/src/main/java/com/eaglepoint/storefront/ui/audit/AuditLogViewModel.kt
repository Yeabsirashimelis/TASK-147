package com.eaglepoint.storefront.ui.audit

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.switchMap
import androidx.lifecycle.viewModelScope
import com.eaglepoint.storefront.domain.model.AuditAction
import com.eaglepoint.storefront.domain.model.AuditEvent
import com.eaglepoint.storefront.domain.usecase.ExportAuditUseCase
import com.eaglepoint.storefront.domain.usecase.GetAuditLogUseCase
import kotlinx.coroutines.launch
import java.io.File

class AuditLogViewModel(
    private val getAuditLogUseCase: GetAuditLogUseCase,
    private val exportAuditUseCase: ExportAuditUseCase
) : ViewModel() {

    private val _currentPage = MutableLiveData(0)

    val auditEvents: LiveData<List<AuditEvent>> = _currentPage.switchMap { page ->
        getAuditLogUseCase.getPage(limit = PAGE_SIZE, offset = page * PAGE_SIZE).asLiveData()
    }

    private val _filterAction = MutableLiveData<AuditAction?>()

    val filteredEvents: LiveData<List<AuditEvent>> = _filterAction.switchMap { action ->
        if (action != null) {
            getAuditLogUseCase.getByAction(action).asLiveData()
        } else {
            getAuditLogUseCase.getPage(PAGE_SIZE, 0).asLiveData()
        }
    }

    private val _exportResult = MutableLiveData<Result<File>?>()
    val exportResult: LiveData<Result<File>?> = _exportResult

    private val _isExporting = MutableLiveData(false)
    val isExporting: LiveData<Boolean> = _isExporting

    fun loadPage(page: Int) {
        _currentPage.value = page
    }

    fun filterByAction(action: AuditAction?) {
        _filterAction.value = action
    }

    fun filterByDateRange(from: Long, to: Long): LiveData<List<AuditEvent>> {
        return getAuditLogUseCase.getByDateRange(from, to).asLiveData()
    }

    fun exportAuditLog(outputFile: File, password: CharArray) {
        _isExporting.value = true
        viewModelScope.launch {
            val result = exportAuditUseCase.export(outputFile, password)
            _exportResult.postValue(result)
            _isExporting.postValue(false)
        }
    }

    companion object {
        private const val PAGE_SIZE = 50
    }
}
