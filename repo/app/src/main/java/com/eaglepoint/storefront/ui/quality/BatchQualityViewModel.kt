package com.eaglepoint.storefront.ui.quality

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.eaglepoint.storefront.domain.model.BatchQualityReport
import com.eaglepoint.storefront.domain.model.DataBatchVersion
import com.eaglepoint.storefront.domain.model.ValidationError
import com.eaglepoint.storefront.domain.usecase.GetBatchQualityReportUseCase
import kotlinx.coroutines.launch

class BatchQualityViewModel(
    private val getBatchQualityReportUseCase: GetBatchQualityReportUseCase
) : ViewModel() {

    val recentBatches: LiveData<List<DataBatchVersion>> =
        getBatchQualityReportUseCase.getRecentBatches().asLiveData()

    val failedBatches: LiveData<List<DataBatchVersion>> =
        getBatchQualityReportUseCase.getFailedBatches().asLiveData()

    private val _selectedReport = MutableLiveData<BatchQualityReport?>()
    val selectedReport: LiveData<BatchQualityReport?> = _selectedReport

    private val _batchErrors = MutableLiveData<List<ValidationError>>()
    val batchErrors: LiveData<List<ValidationError>> = _batchErrors

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    fun loadReport(batchVersionId: String) {
        _isLoading.value = true
        viewModelScope.launch {
            val report = getBatchQualityReportUseCase.getReport(batchVersionId)
            _selectedReport.postValue(report)
            _batchErrors.postValue(report?.errors ?: emptyList())
            _isLoading.postValue(false)
        }
    }
}
