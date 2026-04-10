package com.eaglepoint.storefront.ui.ingestion.log

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.switchMap
import com.eaglepoint.storefront.domain.model.IngestionJobRun
import com.eaglepoint.storefront.domain.usecase.GetIngestionLogsUseCase

class IngestionLogViewModel(
    private val getIngestionLogsUseCase: GetIngestionLogsUseCase
) : ViewModel() {

    private val _sourceRuleIdFilter = MutableLiveData<String?>()

    val jobRuns: LiveData<List<IngestionJobRun>> = _sourceRuleIdFilter.switchMap { sourceRuleId ->
        if (sourceRuleId != null) {
            getIngestionLogsUseCase.getBySourceRule(sourceRuleId).asLiveData()
        } else {
            getIngestionLogsUseCase.getAll().asLiveData()
        }
    }

    fun setSourceFilter(sourceRuleId: String?) {
        _sourceRuleIdFilter.value = sourceRuleId
    }

    init {
        _sourceRuleIdFilter.value = null
    }
}
