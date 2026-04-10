package com.eaglepoint.storefront.domain.model

data class DataBatchVersion(
    val id: String,
    val batchName: String,
    val version: Int,
    val sourceRuleId: String,
    val ruleVersion: Int,
    val itemsCount: Int = 0,
    val ingestionJobRunId: String? = null,
    val errorCount: Int = 0,
    val validationStatus: BatchValidationStatus? = null,
    val errorRate: Double = 0.0,
    val createdAt: Long,
    val updatedAt: Long
)

enum class BatchValidationStatus {
    PENDING,
    PASSED,
    FAILED
}
