package com.eaglepoint.storefront.domain.usecase

import com.eaglepoint.storefront.data.repository.CartRepository
import com.eaglepoint.storefront.data.repository.CatalogRepository
import com.eaglepoint.storefront.domain.model.CartLineItem
import java.util.UUID

class AddToCartUseCase(
    private val cartRepository: CartRepository,
    private val catalogRepository: CatalogRepository
) {

    suspend operator fun invoke(
        cartId: String,
        catalogItemId: String,
        quantity: Int = 1
    ): Result<Unit> {
        if (quantity < 1) return Result.failure(IllegalArgumentException("Quantity must be at least 1"))

        val item = catalogRepository.findItemById(catalogItemId)
            ?: return Result.failure(IllegalArgumentException("Catalog item not found"))

        if (!item.isActive) return Result.failure(IllegalStateException("Item is no longer available"))

        // Validate inventory
        val available = catalogRepository.getAvailableQuantity(catalogItemId)
        if (available < quantity) {
            return Result.failure(IllegalStateException("Insufficient inventory: $available available, $quantity requested"))
        }

        val now = System.currentTimeMillis()
        val lineItem = CartLineItem(
            id = UUID.randomUUID().toString(),
            cartId = cartId,
            catalogItemId = catalogItemId,
            sku = item.sku,
            name = item.name,
            unitPrice = item.price,
            quantity = quantity,
            createdAt = now,
            updatedAt = now
        )

        cartRepository.addItem(cartId, lineItem)
        return Result.success(Unit)
    }
}
