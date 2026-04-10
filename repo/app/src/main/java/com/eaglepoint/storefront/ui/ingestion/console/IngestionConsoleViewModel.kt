package com.eaglepoint.storefront.ui.ingestion.console

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.eaglepoint.storefront.domain.model.IngestionAlert
import com.eaglepoint.storefront.domain.model.IngestionJobRun
import com.eaglepoint.storefront.domain.model.SourceRule
import com.eaglepoint.storefront.domain.usecase.CheckIngestionAlertsUseCase
import com.eaglepoint.storefront.domain.usecase.GetSourceRulesUseCase
import com.eaglepoint.storefront.domain.usecase.RunIngestionUseCase
import com.eaglepoint.storefront.domain.usecase.ScheduleIngestionUseCase
import kotlinx.coroutines.launch

class IngestionConsoleViewModel(
    private val getSourceRulesUseCase: GetSourceRulesUseCase,
    private val runIngestionUseCase: RunIngestionUseCase,
    private val checkAlertsUseCase: CheckIngestionAlertsUseCase,
    private val scheduleIngestionUseCase: ScheduleIngestionUseCase
) : ViewModel() {

    val sourceRules: LiveData<List<SourceRule>> = getSourceRulesUseCase.getAll().asLiveData()

    val unacknowledgedAlerts: LiveData<List<IngestionAlert>> =
        checkAlertsUseCase.getUnacknowledged().asLiveData()

    val alertCount: LiveData<Int> = checkAlertsUseCase.getUnacknowledgedCount().asLiveData()

    private val _runResult = MutableLiveData<Result<IngestionJobRun>>()
    val runResult: LiveData<Result<IngestionJobRun>> = _runResult

    private val _isRunning = MutableLiveData(false)
    val isRunning: LiveData<Boolean> = _isRunning

    fun runIngestionForSource(sourceRuleId: String) {
        _isRunning.value = true
        viewModelScope.launch {
            val result = runIngestionUseCase.runForSource(sourceRuleId)
            _runResult.postValue(result)
            _isRunning.postValue(false)
        }
    }

    fun deactivateSource(sourceRuleId: String) {
        viewModelScope.launch {
            getSourceRulesUseCase.deactivate(sourceRuleId)
        }
    }

    fun acknowledgeAlert(alertId: String) {
        viewModelScope.launch {
            checkAlertsUseCase.acknowledge(alertId)
        }
    }

    fun scheduleIngestion(intervalHours: Long = 6) {
        scheduleIngestionUseCase.schedule(intervalHours, replaceExisting = true)
    }

    fun cancelScheduledIngestion() {
        scheduleIngestionUseCase.cancel()
    }
}
