package com.eaglepoint.storefront.domain.usecase

import com.eaglepoint.storefront.data.repository.CartRepository
import com.eaglepoint.storefront.data.repository.CatalogRepository
import com.eaglepoint.storefront.data.repository.OrderRepository
import com.eaglepoint.storefront.domain.model.Cart
import com.eaglepoint.storefront.domain.model.CartLineItem
import com.eaglepoint.storefront.domain.model.PricingResult
import com.eaglepoint.storefront.security.SessionManager
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class CheckoutUseCaseTest {

    private lateinit var cartRepository: CartRepository
    private lateinit var catalogRepository: CatalogRepository
    private lateinit var calculatePrice: CalculatePriceUseCase
    private lateinit var logAuditEvent: LogAuditEventUseCase
    private lateinit var sessionManager: SessionManager
    private lateinit var orderRepository: OrderRepository
    private lateinit var checkoutUseCase: CheckoutUseCase

    private val now = System.currentTimeMillis()
    private val testItem = CartLineItem(
        id = "li-1", cartId = "cart-1", catalogItemId = "cat-1",
        sku = "SKU-1", name = "Jersey", unitPrice = 49.99, quantity = 2,
        createdAt = now, updatedAt = now
    )
    private val testCart = Cart(
        id = "cart-1", userId = "user-1", items = listOf(testItem),
        createdAt = now, updatedAt = now
    )

    @BeforeEach
    fun setUp() {
        cartRepository = mockk(relaxed = true)
        catalogRepository = mockk(relaxed = true)
        calculatePrice = mockk(relaxed = true)
        logAuditEvent = mockk(relaxed = true)
        sessionManager = mockk(relaxed = true)
        orderRepository = mockk(relaxed = true)

        // Session returns the cart owner by default
        every { sessionManager.requireUserId() } returns "user-1"

        checkoutUseCase = CheckoutUseCase(cartRepository, catalogRepository, calculatePrice, logAuditEvent, sessionManager, orderRepository)

        coEvery { catalogRepository.getAvailableQuantity("cat-1") } returns 10
        coEvery { cartRepository.findById("cart-1") } returns testCart
        coEvery { calculatePrice(any(), any()) } returns PricingResult(
            subtotal = 99.98, total = 107.23, taxAmount = 7.25, taxRate = 0.0725
        )
    }

    @Test
    fun `startCheckout locks prices`() = runTest {
        val result = checkoutUseCase.startCheckout("cart-1")

        assertThat(result.isSuccess).isTrue()
        coVerify { cartRepository.setPriceLock("cart-1", any(), any()) }
    }

    @Test
    fun `startCheckout fails for empty cart`() = runTest {
        coEvery { cartRepository.findById("cart-1") } returns testCart.copy(items = emptyList())

        val result = checkoutUseCase.startCheckout("cart-1")

        assertThat(result.isFailure).isTrue()
    }

    @Test
    fun `startCheckout validates inventory`() = runTest {
        coEvery { catalogRepository.getAvailableQuantity("cat-1") } returns 1

        val result = checkoutUseCase.startCheckout("cart-1")

        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()?.message).contains("Insufficient inventory")
    }

    @Test
    fun `completeCheckout succeeds with valid lock`() = runTest {
        val lockedCart = testCart.copy(
            priceLockedAt = now,
            priceLockExpiresAt = now + 30 * 60 * 1000L
        )
        coEvery { cartRepository.findById("cart-1") } returns lockedCart

        val result = checkoutUseCase.completeCheckout("cart-1", "CA")

        assertThat(result.success).isTrue()
        assertThat(result.orderId).isNotNull()
        assertThat(result.pricing).isNotNull()
    }

    @Test
    fun `completeCheckout requires reconfirm when lock expired`() = runTest {
        val expiredLockCart = testCart.copy(
            priceLockedAt = now - 60 * 60 * 1000L,
            priceLockExpiresAt = now - 30 * 60 * 1000L // expired
        )
        coEvery { cartRepository.findById("cart-1") } returns expiredLockCart

        val result = checkoutUseCase.completeCheckout("cart-1", "CA")

        assertThat(result.success).isFalse()
        assertThat(result.requiresPriceReconfirm).isTrue()
    }

    @Test
    fun `completeCheckout with confirmed price change succeeds`() = runTest {
        val expiredLockCart = testCart.copy(
            priceLockedAt = now - 60 * 60 * 1000L,
            priceLockExpiresAt = now - 30 * 60 * 1000L
        )
        coEvery { cartRepository.findById("cart-1") } returns expiredLockCart

        val result = checkoutUseCase.completeCheckout("cart-1", "CA", confirmedPriceChange = true)

        assertThat(result.success).isTrue()
    }

    @Test
    fun `completeCheckout increments coupon usage`() = runTest {
        val cartWithCoupon = testCart.copy(
            couponId = "coupon-1",
            priceLockedAt = now,
            priceLockExpiresAt = now + 30 * 60 * 1000L
        )
        coEvery { cartRepository.findById("cart-1") } returns cartWithCoupon

        checkoutUseCase.completeCheckout("cart-1", "CA")

        coVerify { catalogRepository.incrementCouponUsage("coupon-1") }
    }

    @Test
    fun `completeCheckout enforces ownership — rejects non-owner`() = runTest {
        every { sessionManager.requireUserId() } returns "user-2"
        every { sessionManager.requireOwnership("user-1") } throws SecurityException("not owner")

        val lockedCart = testCart.copy(
            priceLockedAt = now,
            priceLockExpiresAt = now + 30 * 60 * 1000L
        )
        coEvery { cartRepository.findById("cart-1") } returns lockedCart

        assertThrows<SecurityException> {
            checkoutUseCase.completeCheckout("cart-1", "CA")
        }
    }

    @Test
    fun `completeCheckout derives userId from session for order`() = runTest {
        every { sessionManager.requireUserId() } returns "session-user-id"

        val lockedCart = testCart.copy(
            priceLockedAt = now,
            priceLockExpiresAt = now + 30 * 60 * 1000L
        )
        coEvery { cartRepository.findById("cart-1") } returns lockedCart

        checkoutUseCase.completeCheckout("cart-1", "CA")

        coVerify {
            logAuditEvent(
                userId = "session-user-id",
                action = com.eaglepoint.storefront.domain.model.AuditAction.CHECKOUT_COMPLETED,
                target = "order",
                targetId = any(),
                detail = any()
            )
        }
    }
}
