package com.eaglepoint.storefront.domain.model

data class InventorySnapshot(
    val id: String,
    val catalogItemId: String,
    val quantity: Int,
    val reservedQuantity: Int = 0,
    val batchVersionId: String? = null,
    val createdAt: Long,
    val updatedAt: Long
)
