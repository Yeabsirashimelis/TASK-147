package com.eaglepoint.storefront.domain.usecase

import com.eaglepoint.storefront.data.repository.CartRepository
import com.eaglepoint.storefront.data.repository.CatalogRepository
import com.eaglepoint.storefront.data.repository.OrderRepository
import com.eaglepoint.storefront.domain.model.Cart
import com.eaglepoint.storefront.domain.model.CartLineItem
import com.eaglepoint.storefront.domain.model.Order
import com.eaglepoint.storefront.domain.model.PricingResult
import com.eaglepoint.storefront.security.SessionManager
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class CheckoutReceiptTest {

    private lateinit var cartRepository: CartRepository
    private lateinit var catalogRepository: CatalogRepository
    private lateinit var calculatePrice: CalculatePriceUseCase
    private lateinit var logAuditEvent: LogAuditEventUseCase
    private lateinit var sessionManager: SessionManager
    private lateinit var orderRepository: OrderRepository
    private lateinit var checkoutUseCase: CheckoutUseCase

    private val now = System.currentTimeMillis()
    private val testItem1 = CartLineItem(
        id = "li-1", cartId = "cart-1", catalogItemId = "cat-1",
        sku = "SKU-1", name = "Jersey", unitPrice = 49.99, quantity = 2,
        createdAt = now, updatedAt = now
    )
    private val testItem2 = CartLineItem(
        id = "li-2", cartId = "cart-1", catalogItemId = "cat-2",
        sku = "SKU-2", name = "Cap", unitPrice = 19.99, quantity = 1,
        createdAt = now, updatedAt = now
    )
    private val testCart = Cart(
        id = "cart-1", userId = "user-1",
        items = listOf(testItem1, testItem2),
        priceLockedAt = now,
        priceLockExpiresAt = now + 30 * 60 * 1000L,
        createdAt = now, updatedAt = now
    )
    private val testPricing = PricingResult(
        subtotal = 119.97, discountAmount = 5.0, couponDiscount = 0.0,
        taxAmount = 8.35, taxRate = 0.0725, total = 123.32,
        appliedRules = listOf("Holiday Sale")
    )

    @BeforeEach
    fun setUp() {
        cartRepository = mockk(relaxed = true)
        catalogRepository = mockk(relaxed = true)
        calculatePrice = mockk(relaxed = true)
        logAuditEvent = mockk(relaxed = true)
        sessionManager = mockk(relaxed = true)
        orderRepository = mockk(relaxed = true)

        every { sessionManager.requireUserId() } returns "user-1"

        checkoutUseCase = CheckoutUseCase(
            cartRepository, catalogRepository, calculatePrice,
            logAuditEvent, sessionManager, orderRepository
        )

        coEvery { catalogRepository.getAvailableQuantity(any()) } returns 100
        coEvery { cartRepository.findById("cart-1") } returns testCart
        coEvery { calculatePrice(any(), any()) } returns testPricing
    }

    @Test
    fun `completeCheckout persists order with correct totals`() = runTest {
        val orderSlot = slot<Order>()
        coEvery { orderRepository.createOrder(capture(orderSlot)) } returns Unit

        val result = checkoutUseCase.completeCheckout("cart-1", "CA")

        assertThat(result.success).isTrue()
        assertThat(result.orderId).isNotNull()

        val order = orderSlot.captured
        assertThat(order.userId).isEqualTo("user-1")
        assertThat(order.cartId).isEqualTo("cart-1")
        assertThat(order.subtotal).isEqualTo(119.97)
        assertThat(order.discountAmount).isEqualTo(5.0)
        assertThat(order.taxAmount).isEqualTo(8.35)
        assertThat(order.total).isEqualTo(123.32)
    }

    @Test
    fun `completeCheckout persists order line items matching cart`() = runTest {
        val orderSlot = slot<Order>()
        coEvery { orderRepository.createOrder(capture(orderSlot)) } returns Unit

        checkoutUseCase.completeCheckout("cart-1", "CA")

        val lineItems = orderSlot.captured.lineItems
        assertThat(lineItems).hasSize(2)

        val jerseyItem = lineItems.find { it.sku == "SKU-1" }!!
        assertThat(jerseyItem.name).isEqualTo("Jersey")
        assertThat(jerseyItem.unitPrice).isEqualTo(49.99)
        assertThat(jerseyItem.quantity).isEqualTo(2)
        assertThat(jerseyItem.lineTotal).isEqualTo(99.98)

        val capItem = lineItems.find { it.sku == "SKU-2" }!!
        assertThat(capItem.name).isEqualTo("Cap")
        assertThat(capItem.quantity).isEqualTo(1)
    }

    @Test
    fun `completeCheckout order has same orderId as result`() = runTest {
        val orderSlot = slot<Order>()
        coEvery { orderRepository.createOrder(capture(orderSlot)) } returns Unit

        val result = checkoutUseCase.completeCheckout("cart-1", "CA")

        assertThat(orderSlot.captured.id).isEqualTo(result.orderId)
    }

    @Test
    fun `completeCheckout logs RECEIPT_CREATED audit event`() = runTest {
        checkoutUseCase.completeCheckout("cart-1", "CA")

        coVerify {
            logAuditEvent(
                userId = "user-1",
                action = com.eaglepoint.storefront.domain.model.AuditAction.RECEIPT_CREATED,
                target = "order",
                targetId = any(),
                detail = any()
            )
        }
    }

    @Test
    fun `failed checkout does not persist order`() = runTest {
        coEvery { cartRepository.findById("cart-1") } returns testCart.copy(items = emptyList())

        val result = checkoutUseCase.completeCheckout("cart-1", "CA")

        assertThat(result.success).isFalse()
        coVerify(exactly = 0) { orderRepository.createOrder(any()) }
    }

    @Test
    fun `order userId comes from session not caller`() = runTest {
        every { sessionManager.requireUserId() } returns "session-derived-user"

        val orderSlot = slot<Order>()
        coEvery { orderRepository.createOrder(capture(orderSlot)) } returns Unit

        checkoutUseCase.completeCheckout("cart-1", "CA")

        assertThat(orderSlot.captured.userId).isEqualTo("session-derived-user")
    }
}
