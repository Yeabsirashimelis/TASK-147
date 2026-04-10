package com.eaglepoint.storefront.quality

import com.eaglepoint.storefront.domain.model.BatchQualityReport
import com.eaglepoint.storefront.domain.model.BatchValidationStatus
import com.eaglepoint.storefront.domain.model.DataBatchVersion
import com.eaglepoint.storefront.domain.model.ValidationError

class BatchValidator(
    private val rules: List<ValidationRule> = defaultRules()
) {

    fun validate(
        batchVersion: DataBatchVersion,
        items: List<ValidatableItem>
    ): BatchQualityReport {
        val allErrors = mutableListOf<ValidationError>()

        for (item in items) {
            for (rule in rules) {
                val errors = rule.validate(item)
                allErrors.addAll(errors)
            }
        }

        val totalItems = items.size
        val errorCount = allErrors.size
        val errorRate = if (totalItems > 0) errorCount.toDouble() / totalItems else 0.0
        val passed = errorRate <= BatchQualityReport.ERROR_RATE_THRESHOLD

        val errorsByRule = allErrors.groupBy { it.ruleName }
            .mapValues { it.value.size }

        val errorsByField = allErrors.groupBy { "${it.entityType}.${it.fieldName}" }
            .mapValues { it.value.size }

        val updatedBatch = batchVersion.copy(
            errorCount = errorCount,
            errorRate = errorRate,
            validationStatus = if (passed) BatchValidationStatus.PASSED else BatchValidationStatus.FAILED,
            updatedAt = System.currentTimeMillis()
        )

        return BatchQualityReport(
            batchVersion = updatedBatch,
            totalItems = totalItems,
            errorCount = errorCount,
            errorRate = errorRate,
            passed = passed,
            errors = allErrors,
            errorsByRule = errorsByRule,
            errorsByField = errorsByField
        )
    }

    companion object {
        fun defaultRules(): List<ValidationRule> = listOf(
            PublishTimeRule(),
            PriceRangeRule(),
            InventoryNonNegativeRule()
        )
    }
}
