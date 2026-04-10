package com.eaglepoint.storefront.domain.model

data class IngestionAlert(
    val id: String,
    val sourceRuleId: String,
    val sourceName: String,
    val failureCount: Int,
    val message: String,
    val isAcknowledged: Boolean = false,
    val createdAt: Long
)
