package com.eaglepoint.storefront.domain.usecase

import com.eaglepoint.storefront.data.repository.SourceRuleRepository
import com.eaglepoint.storefront.domain.model.IngestionJobRun
import com.eaglepoint.storefront.ingestion.IngestionEngine
import com.eaglepoint.storefront.security.SessionManager

class RunIngestionUseCase(
    private val ingestionEngine: IngestionEngine,
    private val sourceRuleRepository: SourceRuleRepository,
    private val sessionManager: SessionManager
) {

    suspend fun runAll(): List<IngestionJobRun> {
        val userId = sessionManager.requireUserId()
        return ingestionEngine.runIngestionForAllSources(userId)
    }

    suspend fun runForSource(sourceRuleId: String): Result<IngestionJobRun> {
        val userId = sessionManager.requireUserId()
        val rule = sourceRuleRepository.findById(sourceRuleId)
            ?: return Result.failure(IllegalArgumentException("Source rule not found: $sourceRuleId"))

        if (!rule.isActive) {
            return Result.failure(IllegalStateException("Source rule is not active: ${rule.name}"))
        }

        return try {
            val result = ingestionEngine.runIngestionForSource(rule, userId)
            Result.success(result)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
