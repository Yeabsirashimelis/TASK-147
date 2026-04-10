package com.eaglepoint.storefront.pricing

import com.eaglepoint.storefront.data.db.entity.PriceRuleEntity
import com.eaglepoint.storefront.domain.model.Cart
import com.eaglepoint.storefront.domain.model.CartLineItem
import com.eaglepoint.storefront.domain.model.Coupon
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class PricingEngineTest {

    private lateinit var engine: PricingEngine
    private val now = System.currentTimeMillis()

    private fun lineItem(price: Double, qty: Int = 1) = CartLineItem(
        id = "li-${price}", cartId = "cart-1", catalogItemId = "cat-1",
        sku = "SKU-1", name = "Item", unitPrice = price, quantity = qty,
        createdAt = now, updatedAt = now
    )

    private fun cart(items: List<CartLineItem>) = Cart(
        id = "cart-1", items = items, createdAt = now, updatedAt = now
    )

    private fun percentRule(name: String, percent: Double, minAmount: Double? = null, priority: Int = 0) =
        PriceRuleEntity(
            id = "rule-$name", name = name, type = PricingEngine.RULE_TYPE_PERCENT_OFF,
            minOrderAmount = minAmount, discountPercent = percent,
            isActive = true, priority = priority,
            createdAt = now, updatedAt = now
        )

    @BeforeEach
    fun setUp() {
        engine = PricingEngine()
    }

    @Test
    fun `empty cart returns zero pricing`() {
        val result = engine.calculatePrice(cart(emptyList()), emptyList(), null, 0.0)

        assertThat(result.subtotal).isEqualTo(0.0)
        assertThat(result.total).isEqualTo(0.0)
    }

    @Test
    fun `basic subtotal calculation`() {
        val items = listOf(lineItem(10.0, 3), lineItem(25.0, 2))
        val result = engine.calculatePrice(cart(items), emptyList(), null, 0.0)

        assertThat(result.subtotal).isEqualTo(80.0) // 30 + 50
        assertThat(result.total).isEqualTo(80.0)
    }

    @Test
    fun `10 percent off orders over 50 dollars`() {
        val items = listOf(lineItem(20.0, 3)) // $60 subtotal
        val rules = listOf(percentRule("10% off >$50", 10.0, minAmount = 50.0))

        val result = engine.calculatePrice(cart(items), rules, null, 0.0)

        assertThat(result.subtotal).isEqualTo(60.0)
        assertThat(result.discountAmount).isEqualTo(6.0) // 10% of 60
        assertThat(result.total).isEqualTo(54.0)
        assertThat(result.appliedRules).containsExactly("10% off >$50")
    }

    @Test
    fun `rule does not apply below minimum`() {
        val items = listOf(lineItem(10.0, 2)) // $20 subtotal
        val rules = listOf(percentRule("10% off >$50", 10.0, minAmount = 50.0))

        val result = engine.calculatePrice(cart(items), rules, null, 0.0)

        assertThat(result.discountAmount).isEqualTo(0.0)
        assertThat(result.total).isEqualTo(20.0)
        assertThat(result.appliedRules).isEmpty()
    }

    @Test
    fun `coupon applies percent discount`() {
        val items = listOf(lineItem(50.0))
        val coupon = Coupon(
            id = "coupon-1", code = "SAVE20", discountPercent = 20.0,
            isActive = true
        )

        val result = engine.calculatePrice(cart(items), emptyList(), coupon, 0.0)

        assertThat(result.couponDiscount).isEqualTo(10.0) // 20% of 50
        assertThat(result.total).isEqualTo(40.0)
        assertThat(result.appliedCoupon).isEqualTo("SAVE20")
    }

    @Test
    fun `coupon applies fixed amount discount`() {
        val items = listOf(lineItem(50.0))
        val coupon = Coupon(
            id = "coupon-1", code = "OFF5", discountAmount = 5.0,
            isActive = true
        )

        val result = engine.calculatePrice(cart(items), emptyList(), coupon, 0.0)

        assertThat(result.couponDiscount).isEqualTo(5.0)
        assertThat(result.total).isEqualTo(45.0)
    }

    @Test
    fun `coupon does not apply below minimum order`() {
        val items = listOf(lineItem(10.0))
        val coupon = Coupon(
            id = "coupon-1", code = "BIG", discountPercent = 10.0,
            minOrderAmount = 50.0, isActive = true
        )

        val result = engine.calculatePrice(cart(items), emptyList(), coupon, 0.0)

        assertThat(result.couponDiscount).isEqualTo(0.0)
        assertThat(result.appliedCoupon).isNull()
    }

    @Test
    fun `expired coupon not applied`() {
        val items = listOf(lineItem(50.0))
        val coupon = Coupon(
            id = "coupon-1", code = "OLD", discountPercent = 10.0,
            isActive = true, expiresAt = now - 1000 // expired
        )

        val result = engine.calculatePrice(cart(items), emptyList(), coupon, 0.0)

        assertThat(result.couponDiscount).isEqualTo(0.0)
    }

    @Test
    fun `tax calculated on discounted amount`() {
        val items = listOf(lineItem(100.0))
        val rules = listOf(percentRule("10% off >$50", 10.0, minAmount = 50.0))

        val result = engine.calculatePrice(cart(items), rules, null, 0.0725)

        assertThat(result.subtotal).isEqualTo(100.0)
        assertThat(result.discountAmount).isEqualTo(10.0)
        // Tax on $90 at 7.25%
        assertThat(result.taxAmount).isEqualTo(6.53) // rounded
        assertThat(result.total).isEqualTo(96.53)
    }

    @Test
    fun `combined rule plus coupon plus tax`() {
        val items = listOf(lineItem(20.0, 4)) // $80 subtotal
        val rules = listOf(percentRule("10% off >$50", 10.0, minAmount = 50.0))
        val coupon = Coupon(
            id = "c1", code = "EXTRA5", discountAmount = 5.0, isActive = true
        )

        val result = engine.calculatePrice(cart(items), rules, coupon, 0.08)

        assertThat(result.subtotal).isEqualTo(80.0)
        assertThat(result.discountAmount).isEqualTo(8.0) // 10% of 80
        assertThat(result.couponDiscount).isEqualTo(5.0) // $5 off
        // Tax on 80 - 8 - 5 = 67 at 8%
        assertThat(result.taxAmount).isEqualTo(5.36)
        assertThat(result.total).isEqualTo(72.36) // 67 + 5.36
    }

    @Test
    fun `locked prices used for line totals`() {
        val item = lineItem(50.0).copy(lockedPrice = 45.0)
        val result = engine.calculatePrice(cart(listOf(item)), emptyList(), null, 0.0)

        assertThat(result.subtotal).isEqualTo(45.0) // locked price
    }

    @Test
    fun `price lock duration is 30 minutes`() {
        assertThat(PricingEngine.PRICE_LOCK_DURATION_MS).isEqualTo(30 * 60 * 1000L)
    }

    @Test
    fun `roundCents handles floating point`() {
        assertThat(PricingEngine.roundCents(10.005)).isEqualTo(10.01)
        assertThat(PricingEngine.roundCents(10.004)).isEqualTo(10.0)
        assertThat(PricingEngine.roundCents(0.1 + 0.2)).isEqualTo(0.3)
    }
}
