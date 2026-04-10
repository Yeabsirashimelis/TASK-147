package com.eaglepoint.storefront.domain.model

data class IngestionJobRun(
    val id: String,
    val sourceRuleId: String,
    val ruleVersion: Int,
    val status: IngestionStatus,
    val itemsParsed: Int = 0,
    val itemsStored: Int = 0,
    val failureReason: String? = null,
    val attemptNumber: Int = 1,
    val batchVersionId: String? = null,
    val startedAt: Long,
    val completedAt: Long? = null,
    val createdAt: Long,
    val updatedAt: Long
)

enum class IngestionStatus {
    PENDING,
    RUNNING,
    SUCCESS,
    PARTIAL_FAILURE,
    FAILURE,
    RETRYING
}
