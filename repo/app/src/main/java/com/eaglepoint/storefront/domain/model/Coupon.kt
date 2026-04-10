package com.eaglepoint.storefront.domain.model

data class Coupon(
    val id: String,
    val code: String,
    val description: String? = null,
    val discountPercent: Double? = null,
    val discountAmount: Double? = null,
    val minOrderAmount: Double = 0.0,
    val maxUses: Int? = null,
    val currentUses: Int = 0,
    val isActive: Boolean = true,
    val expiresAt: Long? = null
) {
    val isValid: Boolean
        get() {
            if (!isActive) return false
            if (expiresAt != null && expiresAt < System.currentTimeMillis()) return false
            if (maxUses != null && currentUses >= maxUses) return false
            return true
        }
}
