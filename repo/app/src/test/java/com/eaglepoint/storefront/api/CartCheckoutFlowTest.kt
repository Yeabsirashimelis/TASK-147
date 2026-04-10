package com.eaglepoint.storefront.api

import com.eaglepoint.storefront.data.repository.CartRepository
import com.eaglepoint.storefront.data.repository.CatalogRepository
import com.eaglepoint.storefront.data.repository.OrderRepository
import com.eaglepoint.storefront.domain.model.Cart
import com.eaglepoint.storefront.domain.model.CartLineItem
import com.eaglepoint.storefront.domain.model.CartStatus
import com.eaglepoint.storefront.domain.model.CatalogItem
import com.eaglepoint.storefront.domain.model.Coupon
import com.eaglepoint.storefront.domain.model.PricingResult
import com.eaglepoint.storefront.domain.usecase.AddToCartUseCase
import com.eaglepoint.storefront.domain.usecase.ApplyCouponUseCase
import com.eaglepoint.storefront.domain.usecase.CalculatePriceUseCase
import com.eaglepoint.storefront.domain.usecase.CheckoutUseCase
import com.eaglepoint.storefront.domain.usecase.LogAuditEventUseCase
import com.eaglepoint.storefront.domain.usecase.MergeCartsUseCase
import com.eaglepoint.storefront.domain.usecase.UpdateCartUseCase
import com.eaglepoint.storefront.security.SessionManager
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * API-level functional tests for the cart, pricing, and checkout flow.
 * Covers add/update/remove items, inventory validation, coupon limits,
 * price lock expiry, and checkout state transitions.
 */
class CartCheckoutFlowTest {

    private lateinit var cartRepository: CartRepository
    private lateinit var catalogRepository: CatalogRepository
    private lateinit var orderRepository: OrderRepository
    private lateinit var logAuditEvent: LogAuditEventUseCase
    private lateinit var sessionManager: SessionManager
    private lateinit var addToCartUseCase: AddToCartUseCase
    private lateinit var updateCartUseCase: UpdateCartUseCase
    private lateinit var applyCouponUseCase: ApplyCouponUseCase
    private lateinit var mergeCartsUseCase: MergeCartsUseCase
    private lateinit var calculatePriceUseCase: CalculatePriceUseCase
    private lateinit var checkoutUseCase: CheckoutUseCase

    private val now = System.currentTimeMillis()
    private val testItem = CatalogItem(id = "cat-1", sku = "SKU-001", name = "Jersey", price = 49.99, isActive = true)
    private val lineItem = CartLineItem(
        id = "li-1", cartId = "cart-1", catalogItemId = "cat-1",
        sku = "SKU-001", name = "Jersey", unitPrice = 49.99, quantity = 2,
        createdAt = now, updatedAt = now
    )
    private val testCart = Cart(
        id = "cart-1", userId = "user-1", items = listOf(lineItem),
        createdAt = now, updatedAt = now
    )

    @BeforeEach
    fun setUp() {
        cartRepository = mockk(relaxed = true)
        catalogRepository = mockk(relaxed = true)
        orderRepository = mockk(relaxed = true)
        logAuditEvent = mockk(relaxed = true)
        sessionManager = mockk(relaxed = true)
        calculatePriceUseCase = mockk(relaxed = true)

        every { sessionManager.requireUserId() } returns "user-1"

        addToCartUseCase = AddToCartUseCase(cartRepository, catalogRepository)
        updateCartUseCase = UpdateCartUseCase(cartRepository, catalogRepository)
        applyCouponUseCase = ApplyCouponUseCase(cartRepository, catalogRepository)
        mergeCartsUseCase = MergeCartsUseCase(cartRepository, logAuditEvent)
        checkoutUseCase = CheckoutUseCase(cartRepository, catalogRepository, calculatePriceUseCase, logAuditEvent, sessionManager, orderRepository)

        coEvery { catalogRepository.findItemById("cat-1") } returns testItem
        coEvery { catalogRepository.getAvailableQuantity("cat-1") } returns 10
    }

    // --- Add to cart: normal ---
    @Test
    fun `add item to cart succeeds with available inventory`() = runTest {
        val result = addToCartUseCase("cart-1", "cat-1", 2)
        assertThat(result.isSuccess).isTrue()
        coVerify { cartRepository.addItem("cart-1", any()) }
    }

    // --- Add to cart: item not found ---
    @Test
    fun `add non-existent item returns failure`() = runTest {
        coEvery { catalogRepository.findItemById("missing") } returns null
        val result = addToCartUseCase("cart-1", "missing", 1)
        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()?.message).contains("not found")
    }

    // --- Add to cart: inactive item ---
    @Test
    fun `add inactive item returns failure`() = runTest {
        coEvery { catalogRepository.findItemById("cat-1") } returns testItem.copy(isActive = false)
        val result = addToCartUseCase("cart-1", "cat-1", 1)
        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()?.message).contains("no longer available")
    }

    // --- Add to cart: insufficient inventory ---
    @Test
    fun `add item with insufficient inventory returns failure with quantities`() = runTest {
        coEvery { catalogRepository.getAvailableQuantity("cat-1") } returns 1
        val result = addToCartUseCase("cart-1", "cat-1", 5)
        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()?.message).contains("Insufficient inventory")
        assertThat(result.exceptionOrNull()?.message).contains("1 available")
    }

    // --- Add to cart: zero quantity ---
    @Test
    fun `add item with zero quantity returns failure`() = runTest {
        val result = addToCartUseCase("cart-1", "cat-1", 0)
        assertThat(result.isFailure).isTrue()
    }

    // --- Update quantity: inventory validation ---
    @Test
    fun `update quantity beyond inventory returns failure`() = runTest {
        coEvery { catalogRepository.getAvailableQuantity("cat-1") } returns 3
        val result = updateCartUseCase.updateQuantity("li-1", "cat-1", 10)
        assertThat(result.isFailure).isTrue()
    }

    // --- Update quantity: zero removes ---
    @Test
    fun `update quantity to zero removes item`() = runTest {
        val result = updateCartUseCase.updateQuantity("li-1", "cat-1", 0)
        assertThat(result.isSuccess).isTrue()
        coVerify { cartRepository.removeItem("li-1") }
    }

    // --- Coupon: one per order max ---
    @Test
    fun `apply coupon to cart that already has one returns failure`() = runTest {
        val cartWithCoupon = testCart.copy(couponId = "existing-coupon")
        coEvery { cartRepository.findById("cart-1") } returns cartWithCoupon
        coEvery { catalogRepository.findCouponByCode("SAVE20") } returns
            Coupon(id = "c-1", code = "SAVE20", discountPercent = 20.0, isActive = true)

        val result = applyCouponUseCase.apply("cart-1", "SAVE20")

        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()?.message).contains("one coupon")
    }

    // --- Coupon: invalid code ---
    @Test
    fun `apply invalid coupon code returns failure`() = runTest {
        coEvery { catalogRepository.findCouponByCode("INVALID") } returns null

        val result = applyCouponUseCase.apply("cart-1", "INVALID")

        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()?.message).contains("Invalid coupon")
    }

    // --- Coupon: expired ---
    @Test
    fun `apply expired coupon returns failure`() = runTest {
        val expiredCoupon = Coupon(
            id = "c-1", code = "EXPIRED", discountPercent = 10.0,
            isActive = true, expiresAt = now - 1000
        )
        coEvery { catalogRepository.findCouponByCode("EXPIRED") } returns expiredCoupon
        coEvery { cartRepository.findById("cart-1") } returns testCart

        val result = applyCouponUseCase.apply("cart-1", "EXPIRED")

        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()?.message).contains("expired")
    }

    // --- Checkout: empty cart ---
    @Test
    fun `checkout with empty cart returns failure`() = runTest {
        coEvery { cartRepository.findById("cart-1") } returns testCart.copy(items = emptyList())

        val result = checkoutUseCase.startCheckout("cart-1")

        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()?.message).contains("empty")
    }

    // --- Checkout: inventory check at checkout time ---
    @Test
    fun `checkout fails when inventory drops below cart quantity`() = runTest {
        coEvery { cartRepository.findById("cart-1") } returns testCart
        coEvery { catalogRepository.getAvailableQuantity("cat-1") } returns 1

        val result = checkoutUseCase.startCheckout("cart-1")

        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()?.message).contains("Insufficient inventory")
    }

    // --- Checkout: price lock ---
    @Test
    fun `startCheckout locks prices for 30 minutes`() = runTest {
        coEvery { cartRepository.findById("cart-1") } returns testCart

        val result = checkoutUseCase.startCheckout("cart-1")

        assertThat(result.isSuccess).isTrue()
        coVerify { cartRepository.setPriceLock("cart-1", any(), any()) }
    }

    // --- Checkout: expired price lock requires reconfirm ---
    @Test
    fun `complete checkout with expired lock requires price reconfirmation`() = runTest {
        val expiredCart = testCart.copy(
            priceLockedAt = now - 3600000,
            priceLockExpiresAt = now - 1800000
        )
        coEvery { cartRepository.findById("cart-1") } returns expiredCart
        coEvery { calculatePriceUseCase(any(), any()) } returns PricingResult(subtotal = 99.98, total = 107.23, taxAmount = 7.25, taxRate = 0.0725)

        val result = checkoutUseCase.completeCheckout("cart-1", "CA")

        assertThat(result.success).isFalse()
        assertThat(result.requiresPriceReconfirm).isTrue()
    }

    // --- Guest-to-user cart merge ---
    @Test
    fun `merge guest cart into user cart succeeds`() = runTest {
        val mergedCart = testCart.copy(id = "user-cart")
        coEvery { cartRepository.mergeGuestCartIntoUser("guest-cart", "user-1") } returns mergedCart

        val result = mergeCartsUseCase("guest-cart", "user-1")

        assertThat(result.isSuccess).isTrue()
        assertThat(result.getOrNull()?.id).isEqualTo("user-cart")
    }

    // --- Data state verification ---
    @Test
    fun `successful checkout marks cart as CHECKED_OUT`() = runTest {
        val lockedCart = testCart.copy(
            priceLockedAt = now, priceLockExpiresAt = now + 1800000
        )
        coEvery { cartRepository.findById("cart-1") } returns lockedCart
        coEvery { calculatePriceUseCase(any(), any()) } returns PricingResult(subtotal = 99.98, total = 107.23, taxAmount = 7.25, taxRate = 0.0725)

        checkoutUseCase.completeCheckout("cart-1", "CA")

        coVerify { cartRepository.updateStatus("cart-1", CartStatus.CHECKED_OUT) }
    }

    @Test
    fun `checkout increments coupon usage when applied`() = runTest {
        val cartWithCoupon = testCart.copy(
            couponId = "coupon-1",
            priceLockedAt = now, priceLockExpiresAt = now + 1800000
        )
        coEvery { cartRepository.findById("cart-1") } returns cartWithCoupon
        coEvery { calculatePriceUseCase(any(), any()) } returns PricingResult(subtotal = 99.98, total = 90.0, taxAmount = 0.0, taxRate = 0.0)

        checkoutUseCase.completeCheckout("cart-1", "CA")

        coVerify { catalogRepository.incrementCouponUsage("coupon-1") }
    }
}
