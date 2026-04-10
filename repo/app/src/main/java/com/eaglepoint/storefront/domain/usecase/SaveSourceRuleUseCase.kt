package com.eaglepoint.storefront.domain.usecase

import com.eaglepoint.storefront.data.repository.SourceRuleRepository
import com.eaglepoint.storefront.domain.model.AuditAction
import com.eaglepoint.storefront.domain.model.SourceRule
import com.eaglepoint.storefront.domain.validation.InputValidator
import com.eaglepoint.storefront.domain.validation.ValidationResult
import com.eaglepoint.storefront.security.SessionManager

class SaveSourceRuleUseCase(
    private val sourceRuleRepository: SourceRuleRepository,
    private val inputValidator: InputValidator,
    private val logAuditEvent: LogAuditEventUseCase,
    private val roleGuard: RoleGuard,
    private val sessionManager: SessionManager
) {

    suspend operator fun invoke(rule: SourceRule): Result<SourceRule> {
        roleGuard.enforceSourceRuleAccess()
        val userId = sessionManager.requireUserId()
        // Validate name
        val nameValidation = inputValidator.validateFreeText(rule.name, maxLength = 100)
        if (nameValidation is ValidationResult.Invalid) {
            return Result.failure(IllegalArgumentException("Invalid name: ${nameValidation.reasons.first()}"))
        }

        // Validate URL format
        if (rule.url.isBlank() || !rule.url.matches(Regex("^https?://.*"))) {
            return Result.failure(IllegalArgumentException("Invalid URL: must start with http:// or https://"))
        }

        // Validate interval
        if (rule.intervalHours < 1 || rule.intervalHours > 168) {
            return Result.failure(IllegalArgumentException("Interval must be between 1 and 168 hours"))
        }

        return try {
            val existing = sourceRuleRepository.findById(rule.id)
            val isNew = existing == null
            val savedRule = if (isNew) {
                rule
            } else {
                rule.copy(ruleVersion = existing!!.ruleVersion + 1)
            }

            sourceRuleRepository.save(savedRule)

            logAuditEvent(
                userId = userId,
                action = if (isNew) AuditAction.SOURCE_RULE_CREATED else AuditAction.SOURCE_RULE_UPDATED,
                target = "source_rule",
                targetId = rule.id,
                detail = "${rule.name} (v${savedRule.ruleVersion})"
            )

            Result.success(savedRule)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
