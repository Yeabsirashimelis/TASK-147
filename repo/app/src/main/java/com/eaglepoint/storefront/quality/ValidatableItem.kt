package com.eaglepoint.storefront.quality

data class ValidatableItem(
    val entityType: String,
    val entityId: String,
    val batchVersionId: String,
    val fields: Map<String, Any?>
) {
    companion object {
        fun fromArticle(
            id: String,
            batchVersionId: String,
            publishedAt: Long?,
            title: String?,
            externalUrl: String?
        ): ValidatableItem {
            return ValidatableItem(
                entityType = "article",
                entityId = id,
                batchVersionId = batchVersionId,
                fields = mapOf(
                    "publishedAt" to publishedAt,
                    "title" to title,
                    "externalUrl" to externalUrl
                )
            )
        }

        fun fromCatalogItem(
            id: String,
            batchVersionId: String,
            price: Double?,
            name: String?
        ): ValidatableItem {
            return ValidatableItem(
                entityType = "catalog_item",
                entityId = id,
                batchVersionId = batchVersionId,
                fields = mapOf(
                    "price" to price,
                    "name" to name
                )
            )
        }

        fun fromInventorySnapshot(
            id: String,
            batchVersionId: String,
            quantity: Int?,
            catalogItemId: String?
        ): ValidatableItem {
            return ValidatableItem(
                entityType = "inventory_snapshot",
                entityId = id,
                batchVersionId = batchVersionId,
                fields = mapOf(
                    "quantity" to quantity,
                    "catalogItemId" to catalogItemId
                )
            )
        }
    }
}
