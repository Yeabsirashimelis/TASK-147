package com.eaglepoint.storefront.domain.model

data class Cart(
    val id: String,
    val userId: String? = null,
    val status: CartStatus = CartStatus.ACTIVE,
    val couponId: String? = null,
    val priceLockedAt: Long? = null,
    val priceLockExpiresAt: Long? = null,
    val items: List<CartLineItem> = emptyList(),
    val createdAt: Long,
    val updatedAt: Long
) {
    val isPriceLocked: Boolean
        get() = priceLockExpiresAt != null && priceLockExpiresAt > System.currentTimeMillis()

    val isPriceLockExpired: Boolean
        get() = priceLockExpiresAt != null && priceLockExpiresAt <= System.currentTimeMillis()

    val isGuest: Boolean
        get() = userId == null
}

enum class CartStatus {
    ACTIVE,
    CHECKED_OUT,
    ABANDONED,
    MERGED
}

data class CartLineItem(
    val id: String,
    val cartId: String,
    val catalogItemId: String,
    val sku: String,
    val name: String,
    val unitPrice: Double,
    val quantity: Int = 1,
    val lockedPrice: Double? = null,
    val createdAt: Long,
    val updatedAt: Long
) {
    val effectivePrice: Double
        get() = lockedPrice ?: unitPrice

    val lineTotal: Double
        get() = effectivePrice * quantity
}
