package com.eaglepoint.storefront.ui.ingestion.editor

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eaglepoint.storefront.domain.model.FeedType
import com.eaglepoint.storefront.domain.model.SourceRule
import com.eaglepoint.storefront.domain.usecase.GetSourceRulesUseCase
import com.eaglepoint.storefront.domain.usecase.SaveSourceRuleUseCase
import kotlinx.coroutines.launch
import java.util.UUID

class SourceRuleEditorViewModel(
    private val saveSourceRuleUseCase: SaveSourceRuleUseCase,
    private val getSourceRulesUseCase: GetSourceRulesUseCase
) : ViewModel() {

    private val _sourceRule = MutableLiveData<SourceRule?>()
    val sourceRule: LiveData<SourceRule?> = _sourceRule

    private val _saveResult = MutableLiveData<Result<SourceRule>>()
    val saveResult: LiveData<Result<SourceRule>> = _saveResult

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    fun loadSourceRule(id: String) {
        viewModelScope.launch {
            val rule = getSourceRulesUseCase.findById(id)
            _sourceRule.postValue(rule)
        }
    }

    fun save(
        existingId: String?,
        name: String,
        url: String,
        feedType: FeedType,
        parseSelector: String?,
        allowedDomains: String,
        blockedDomains: String,
        allowedKeywords: String,
        blockedKeywords: String,
        intervalHours: Int,
        requestDelayMs: Long
    ) {
        _isLoading.value = true
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val existing = existingId?.let { _sourceRule.value }

            val rule = SourceRule(
                id = existingId ?: UUID.randomUUID().toString(),
                name = name.trim(),
                url = url.trim(),
                feedType = feedType,
                parseSelector = parseSelector?.trim()?.ifBlank { null },
                allowedDomains = allowedDomains.split(",").map { it.trim() }.filter { it.isNotBlank() },
                blockedDomains = blockedDomains.split(",").map { it.trim() }.filter { it.isNotBlank() },
                allowedKeywords = allowedKeywords.split(",").map { it.trim() }.filter { it.isNotBlank() },
                blockedKeywords = blockedKeywords.split(",").map { it.trim() }.filter { it.isNotBlank() },
                intervalHours = intervalHours,
                requestDelayMs = requestDelayMs,
                isActive = existing?.isActive ?: true,
                ruleVersion = existing?.ruleVersion ?: 1,
                createdAt = existing?.createdAt ?: now,
                updatedAt = now
            )

            val result = saveSourceRuleUseCase(rule)
            _saveResult.postValue(result)
            _isLoading.postValue(false)
        }
    }
}
