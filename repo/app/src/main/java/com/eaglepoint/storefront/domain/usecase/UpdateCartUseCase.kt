package com.eaglepoint.storefront.domain.usecase

import com.eaglepoint.storefront.data.repository.CartRepository
import com.eaglepoint.storefront.data.repository.CatalogRepository

class UpdateCartUseCase(
    private val cartRepository: CartRepository,
    private val catalogRepository: CatalogRepository
) {

    suspend fun updateQuantity(itemId: String, catalogItemId: String, quantity: Int): Result<Unit> {
        if (quantity < 0) return Result.failure(IllegalArgumentException("Quantity cannot be negative"))

        if (quantity == 0) {
            cartRepository.removeItem(itemId)
            return Result.success(Unit)
        }

        // Validate inventory
        val available = catalogRepository.getAvailableQuantity(catalogItemId)
        if (available < quantity) {
            return Result.failure(IllegalStateException("Insufficient inventory: $available available, $quantity requested"))
        }

        cartRepository.updateItemQuantity(itemId, quantity)
        return Result.success(Unit)
    }

    suspend fun removeItem(itemId: String): Result<Unit> {
        cartRepository.removeItem(itemId)
        return Result.success(Unit)
    }

    suspend fun clearCart(cartId: String): Result<Unit> {
        cartRepository.clearCart(cartId)
        return Result.success(Unit)
    }
}
