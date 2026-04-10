package com.eaglepoint.storefront.domain.model

data class CatalogItem(
    val id: String,
    val sku: String,
    val name: String,
    val description: String? = null,
    val price: Double,
    val imageUrl: String? = null,
    val category: String? = null,
    val isActive: Boolean = true
)
