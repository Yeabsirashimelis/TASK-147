package com.eaglepoint.storefront.pricing

import com.eaglepoint.storefront.data.db.entity.PriceRuleEntity
import com.eaglepoint.storefront.domain.model.Cart
import com.eaglepoint.storefront.domain.model.Coupon
import com.eaglepoint.storefront.domain.model.PricingResult
import kotlin.math.max
import kotlin.math.roundToLong

class PricingEngine {

    fun calculatePrice(
        cart: Cart,
        priceRules: List<PriceRuleEntity>,
        coupon: Coupon?,
        taxRate: Double
    ): PricingResult {
        if (cart.items.isEmpty()) return PricingResult.EMPTY

        // 1. Calculate subtotal from line items
        val subtotal = cart.items.sumOf { it.lineTotal }

        // 2. Apply price rules (sorted by priority DESC)
        var ruleDiscount = 0.0
        val appliedRules = mutableListOf<String>()

        val sortedRules = priceRules.sortedByDescending { it.priority }
        for (rule in sortedRules) {
            val discount = applyRule(rule, subtotal)
            if (discount > 0.0) {
                ruleDiscount += discount
                appliedRules.add(rule.name)
            }
        }

        // 3. Apply coupon (one coupon max per order)
        var couponDiscount = 0.0
        var appliedCouponCode: String? = null
        if (coupon != null && coupon.isValid) {
            val afterRules = subtotal - ruleDiscount
            if (afterRules >= coupon.minOrderAmount) {
                couponDiscount = when {
                    coupon.discountPercent != null -> roundCents(afterRules * coupon.discountPercent / 100.0)
                    coupon.discountAmount != null -> minOf(coupon.discountAmount, afterRules)
                    else -> 0.0
                }
                appliedCouponCode = coupon.code
            }
        }

        // 4. Calculate taxable amount and tax
        val totalDiscount = ruleDiscount + couponDiscount
        val taxableAmount = max(0.0, subtotal - totalDiscount)
        val taxAmount = roundCents(taxableAmount * taxRate)

        // 5. Final total
        val total = roundCents(taxableAmount + taxAmount)

        return PricingResult(
            subtotal = roundCents(subtotal),
            discountAmount = roundCents(ruleDiscount),
            couponDiscount = roundCents(couponDiscount),
            taxAmount = taxAmount,
            taxRate = taxRate,
            total = total,
            appliedRules = appliedRules,
            appliedCoupon = appliedCouponCode
        )
    }

    private fun applyRule(rule: PriceRuleEntity, subtotal: Double): Double {
        return when (rule.type) {
            RULE_TYPE_PERCENT_OFF -> {
                val minAmount = rule.minOrderAmount ?: 0.0
                if (subtotal >= minAmount && rule.discountPercent != null) {
                    roundCents(subtotal * rule.discountPercent / 100.0)
                } else 0.0
            }
            RULE_TYPE_AMOUNT_OFF -> {
                val minAmount = rule.minOrderAmount ?: 0.0
                if (subtotal >= minAmount && rule.discountAmount != null) {
                    minOf(rule.discountAmount, subtotal)
                } else 0.0
            }
            else -> 0.0
        }
    }

    companion object {
        const val RULE_TYPE_PERCENT_OFF = "PERCENT_OFF"
        const val RULE_TYPE_AMOUNT_OFF = "AMOUNT_OFF"
        const val PRICE_LOCK_DURATION_MS = 30 * 60 * 1000L // 30 minutes

        fun roundCents(amount: Double): Double {
            return (amount * 100.0).roundToLong() / 100.0
        }
    }
}
