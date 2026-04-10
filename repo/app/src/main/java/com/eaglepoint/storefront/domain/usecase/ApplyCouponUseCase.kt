package com.eaglepoint.storefront.domain.usecase

import com.eaglepoint.storefront.data.repository.CartRepository
import com.eaglepoint.storefront.data.repository.CatalogRepository
import com.eaglepoint.storefront.domain.model.Coupon

class ApplyCouponUseCase(
    private val cartRepository: CartRepository,
    private val catalogRepository: CatalogRepository
) {

    suspend fun apply(cartId: String, couponCode: String): Result<Coupon> {
        val coupon = catalogRepository.findCouponByCode(couponCode.trim().uppercase())
            ?: return Result.failure(IllegalArgumentException("Invalid coupon code"))

        if (!coupon.isValid) {
            return Result.failure(IllegalStateException("Coupon is expired or has reached maximum uses"))
        }

        // Check if cart already has a coupon (one coupon max per order)
        val cart = cartRepository.findById(cartId)
            ?: return Result.failure(IllegalArgumentException("Cart not found"))

        if (cart.couponId != null) {
            return Result.failure(IllegalStateException("Only one coupon per order is allowed. Remove the existing coupon first."))
        }

        // Check minimum order amount
        val subtotal = cart.items.sumOf { it.lineTotal }
        if (subtotal < coupon.minOrderAmount) {
            return Result.failure(IllegalStateException(
                "Order subtotal ($${"%.2f".format(subtotal)}) does not meet minimum ($${"%.2f".format(coupon.minOrderAmount)})"
            ))
        }

        cartRepository.setCoupon(cartId, coupon.id)
        return Result.success(coupon)
    }

    suspend fun remove(cartId: String): Result<Unit> {
        cartRepository.setCoupon(cartId, null)
        return Result.success(Unit)
    }
}
