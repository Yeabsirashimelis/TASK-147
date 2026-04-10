package com.eaglepoint.storefront.domain.usecase

import com.eaglepoint.storefront.data.repository.SourceRuleRepository
import com.eaglepoint.storefront.domain.model.AuditAction
import com.eaglepoint.storefront.domain.model.SourceRule
import com.eaglepoint.storefront.security.SessionManager
import kotlinx.coroutines.flow.Flow

class GetSourceRulesUseCase(
    private val sourceRuleRepository: SourceRuleRepository,
    private val logAuditEvent: LogAuditEventUseCase,
    private val sessionManager: SessionManager
) {

    fun getAll(): Flow<List<SourceRule>> {
        return sourceRuleRepository.getAll()
    }

    fun getAllActive(): Flow<List<SourceRule>> {
        return sourceRuleRepository.getAllActive()
    }

    suspend fun findById(id: String): SourceRule? {
        return sourceRuleRepository.findById(id)
    }

    suspend fun deactivate(id: String) {
        val userId = sessionManager.requireUserId()
        val rule = sourceRuleRepository.findById(id) ?: return
        sourceRuleRepository.deactivate(id, System.currentTimeMillis())
        logAuditEvent(
            userId = userId,
            action = AuditAction.SOURCE_RULE_DEACTIVATED,
            target = "source_rule",
            targetId = id,
            detail = "Deactivated: ${rule.name}"
        )
    }
}
