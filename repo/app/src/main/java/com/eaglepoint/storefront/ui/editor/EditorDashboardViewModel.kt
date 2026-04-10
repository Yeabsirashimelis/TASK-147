package com.eaglepoint.storefront.ui.editor

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import com.eaglepoint.storefront.domain.model.DataBatchVersion
import com.eaglepoint.storefront.domain.model.IngestionJobRun
import com.eaglepoint.storefront.domain.usecase.ReviewIngestionUseCase

class EditorDashboardViewModel(
    private val reviewIngestionUseCase: ReviewIngestionUseCase
) : ViewModel() {

    fun getRecentJobRuns(sourceRuleId: String? = null): LiveData<List<IngestionJobRun>> {
        return reviewIngestionUseCase.getJobRuns(
            sourceRuleId = sourceRuleId
        ).asLiveData()
    }

    fun getBatchQualityHistory(): LiveData<List<DataBatchVersion>> {
        return reviewIngestionUseCase.getBatchQualityHistory().asLiveData()
    }

    fun getFailedBatches(): LiveData<List<DataBatchVersion>> {
        return reviewIngestionUseCase.getFailedBatches().asLiveData()
    }

    val pendingReviewCount: LiveData<Int> by lazy {
        try {
            reviewIngestionUseCase.getPendingReviewCount().asLiveData()
        } catch (e: SecurityException) {
            MutableLiveData(0)
        }
    }
}
