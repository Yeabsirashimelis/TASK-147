package com.eaglepoint.storefront.domain.usecase

import com.eaglepoint.storefront.data.repository.CartRepository
import com.eaglepoint.storefront.data.repository.CatalogRepository
import com.eaglepoint.storefront.data.repository.OrderRepository
import com.eaglepoint.storefront.domain.model.AuditAction
import com.eaglepoint.storefront.domain.model.CartStatus
import com.eaglepoint.storefront.domain.model.CheckoutResult
import com.eaglepoint.storefront.domain.model.Order
import com.eaglepoint.storefront.domain.model.OrderLineItem
import com.eaglepoint.storefront.pricing.PricingEngine
import com.eaglepoint.storefront.security.SessionManager
import java.util.UUID

class CheckoutUseCase(
    private val cartRepository: CartRepository,
    private val catalogRepository: CatalogRepository,
    private val calculatePrice: CalculatePriceUseCase,
    private val logAuditEvent: LogAuditEventUseCase,
    private val sessionManager: SessionManager,
    private val orderRepository: OrderRepository
) {

    suspend fun startCheckout(cartId: String): Result<Unit> {
        val userId = sessionManager.requireUserId()
        val cart = cartRepository.findById(cartId)
            ?: return Result.failure(IllegalArgumentException("Cart not found"))

        sessionManager.requireOwnership(cart.userId)

        if (cart.items.isEmpty()) {
            return Result.failure(IllegalStateException("Cart is empty"))
        }

        // Validate inventory for all items
        for (item in cart.items) {
            val available = catalogRepository.getAvailableQuantity(item.catalogItemId)
            if (available < item.quantity) {
                return Result.failure(IllegalStateException(
                    "Insufficient inventory for '${item.name}': $available available, ${item.quantity} in cart"
                ))
            }
        }

        // Lock prices for 30 minutes
        val now = System.currentTimeMillis()
        cartRepository.setPriceLock(cartId, now, now + PricingEngine.PRICE_LOCK_DURATION_MS)

        logAuditEvent(
            userId = userId,
            action = AuditAction.CHECKOUT_STARTED,
            target = "cart",
            targetId = cartId,
            detail = "Price locked for 30 minutes"
        )

        return Result.success(Unit)
    }

    suspend fun completeCheckout(
        cartId: String,
        stateCode: String,
        confirmedPriceChange: Boolean = false
    ): CheckoutResult {
        val userId = sessionManager.requireUserId()
        val cart = cartRepository.findById(cartId)
            ?: return CheckoutResult(success = false, error = "Cart not found")

        // Enforce session ownership — caller cannot act on another user's cart
        sessionManager.requireOwnership(cart.userId)

        if (cart.items.isEmpty()) {
            return CheckoutResult(success = false, error = "Cart is empty")
        }

        // Check price lock
        if (cart.isPriceLockExpired && !confirmedPriceChange) {
            // Prices may have changed — require reconfirmation
            cartRepository.clearPriceLock(cartId)
            return CheckoutResult(
                success = false,
                error = "Price lock expired. Please review updated prices.",
                requiresPriceReconfirm = true
            )
        }

        // Final inventory validation
        for (item in cart.items) {
            val available = catalogRepository.getAvailableQuantity(item.catalogItemId)
            if (available < item.quantity) {
                return CheckoutResult(
                    success = false,
                    error = "Insufficient inventory for '${item.name}': $available available"
                )
            }
        }

        // Calculate final pricing
        val pricing = calculatePrice(cart, stateCode)

        // Generate order ID and mark cart as checked out
        val orderId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()

        // Persist order with line items — userId derived from session, not caller
        val orderLineItems = cart.items.map { item ->
            OrderLineItem(
                id = UUID.randomUUID().toString(),
                orderId = orderId,
                catalogItemId = item.catalogItemId,
                sku = item.sku,
                name = item.name,
                unitPrice = item.effectivePrice,
                quantity = item.quantity,
                lineTotal = item.lineTotal,
                createdAt = now
            )
        }

        val order = Order(
            id = orderId,
            userId = userId,
            cartId = cartId,
            subtotal = pricing.subtotal,
            discountAmount = pricing.discountAmount,
            couponDiscount = pricing.couponDiscount,
            taxAmount = pricing.taxAmount,
            taxRate = pricing.taxRate,
            total = pricing.total,
            appliedCoupon = pricing.appliedCoupon,
            lineItems = orderLineItems,
            createdAt = now,
            updatedAt = now
        )
        orderRepository.createOrder(order)

        cartRepository.updateStatus(cartId, CartStatus.CHECKED_OUT)

        // Increment coupon usage if applied
        if (cart.couponId != null) {
            catalogRepository.incrementCouponUsage(cart.couponId)
        }

        logAuditEvent(
            userId = userId,
            action = AuditAction.CHECKOUT_COMPLETED,
            target = "order",
            targetId = orderId,
            detail = "Order $orderId — Total: $${"%.2f".format(pricing.total)}"
        )

        logAuditEvent(
            userId = userId,
            action = AuditAction.RECEIPT_CREATED,
            target = "order",
            targetId = orderId,
            detail = "Receipt for order $orderId, ${orderLineItems.size} items"
        )

        return CheckoutResult(
            success = true,
            orderId = orderId,
            pricing = pricing
        )
    }
}
