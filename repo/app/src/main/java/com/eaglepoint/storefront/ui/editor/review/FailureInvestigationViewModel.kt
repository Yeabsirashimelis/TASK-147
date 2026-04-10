package com.eaglepoint.storefront.ui.editor.review

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.switchMap
import com.eaglepoint.storefront.domain.model.IngestionJobRun
import com.eaglepoint.storefront.domain.model.ValidationError
import com.eaglepoint.storefront.domain.usecase.ReviewIngestionUseCase

class FailureInvestigationViewModel(
    private val reviewIngestionUseCase: ReviewIngestionUseCase
) : ViewModel() {

    private val _sourceFilter = MutableLiveData<String?>(null)

    val jobRuns: LiveData<List<IngestionJobRun>> = _sourceFilter.switchMap { sourceId ->
        reviewIngestionUseCase.getJobRuns(
            sourceRuleId = sourceId
        ).asLiveData()
    }

    private val _selectedBatchId = MutableLiveData<String?>()
    val validationErrors: LiveData<List<ValidationError>> = _selectedBatchId.switchMap { batchId ->
        if (batchId != null) {
            reviewIngestionUseCase.getValidationErrors(batchId).asLiveData()
        } else {
            MutableLiveData(emptyList())
        }
    }

    fun setSourceFilter(sourceRuleId: String?) {
        _sourceFilter.value = sourceRuleId
    }

    fun loadValidationErrors(batchVersionId: String) {
        _selectedBatchId.value = batchVersionId
    }
}
