package com.eaglepoint.storefront.domain.model

data class ValidationError(
    val id: String,
    val batchVersionId: String,
    val entityType: String,
    val entityId: String,
    val fieldName: String,
    val ruleName: String,
    val message: String,
    val actualValue: String? = null,
    val severity: ValidationSeverity = ValidationSeverity.ERROR,
    val createdAt: Long
)

enum class ValidationSeverity {
    WARNING,
    ERROR
}
