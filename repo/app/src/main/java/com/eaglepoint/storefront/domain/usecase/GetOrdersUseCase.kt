package com.eaglepoint.storefront.domain.usecase

import com.eaglepoint.storefront.data.repository.OrderRepository
import com.eaglepoint.storefront.domain.model.AuditAction
import com.eaglepoint.storefront.domain.model.Order
import com.eaglepoint.storefront.security.SessionManager
import kotlinx.coroutines.flow.Flow

class GetOrdersUseCase(
    private val orderRepository: OrderRepository,
    private val sessionManager: SessionManager,
    private val logAuditEvent: LogAuditEventUseCase
) {

    fun getMyOrders(): Flow<List<Order>> {
        val userId = sessionManager.requireUserId()
        return orderRepository.getByUserId(userId)
    }

    suspend fun getOrderDetail(orderId: String): Order? {
        val order = orderRepository.findById(orderId) ?: return null
        sessionManager.requireOwnership(order.userId)

        logAuditEvent(
            userId = sessionManager.requireUserId(),
            action = AuditAction.RECEIPT_VIEWED,
            target = "order",
            targetId = orderId
        )

        return order
    }
}
