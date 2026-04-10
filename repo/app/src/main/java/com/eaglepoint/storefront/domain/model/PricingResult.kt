package com.eaglepoint.storefront.domain.model

data class PricingResult(
    val subtotal: Double,
    val discountAmount: Double = 0.0,
    val couponDiscount: Double = 0.0,
    val taxAmount: Double = 0.0,
    val taxRate: Double = 0.0,
    val total: Double,
    val appliedRules: List<String> = emptyList(),
    val appliedCoupon: String? = null
) {
    companion object {
        val EMPTY = PricingResult(subtotal = 0.0, total = 0.0)
    }
}

data class CheckoutResult(
    val success: Boolean,
    val orderId: String? = null,
    val pricing: PricingResult? = null,
    val error: String? = null,
    val requiresPriceReconfirm: Boolean = false
)
