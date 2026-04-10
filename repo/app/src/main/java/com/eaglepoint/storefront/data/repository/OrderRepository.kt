package com.eaglepoint.storefront.data.repository

import com.eaglepoint.storefront.data.db.dao.OrderDao
import com.eaglepoint.storefront.data.db.entity.OrderEntity
import com.eaglepoint.storefront.data.db.entity.OrderLineItemEntity
import com.eaglepoint.storefront.domain.model.Order
import com.eaglepoint.storefront.domain.model.OrderLineItem
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class OrderRepository(
    private val orderDao: OrderDao,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    suspend fun createOrder(order: Order) = withContext(dispatcher) {
        orderDao.insertOrder(orderToEntity(order))
        if (order.lineItems.isNotEmpty()) {
            orderDao.insertLineItems(order.lineItems.map { lineItemToEntity(it) })
        }
    }

    suspend fun findById(id: String): Order? = withContext(dispatcher) {
        val entity = orderDao.findById(id) ?: return@withContext null
        val lineItems = orderDao.getLineItemsByOrderId(id)
        orderEntityToDomain(entity, lineItems)
    }

    fun getByUserId(userId: String): Flow<List<Order>> {
        return orderDao.getByUserId(userId).map { orders ->
            orders.map { entity ->
                val items = orderDao.getLineItemsByOrderId(entity.id)
                orderEntityToDomain(entity, items)
            }
        }
    }

    suspend fun getByUserIdList(userId: String): List<Order> = withContext(dispatcher) {
        orderDao.getByUserIdList(userId).map { entity ->
            val items = orderDao.getLineItemsByOrderId(entity.id)
            orderEntityToDomain(entity, items)
        }
    }

    private fun orderEntityToDomain(entity: OrderEntity, lineItems: List<OrderLineItemEntity>): Order {
        return Order(
            id = entity.id,
            userId = entity.userId,
            cartId = entity.cartId,
            subtotal = entity.subtotal,
            discountAmount = entity.discountAmount,
            couponDiscount = entity.couponDiscount,
            taxAmount = entity.taxAmount,
            taxRate = entity.taxRate,
            total = entity.total,
            appliedCoupon = entity.appliedCoupon,
            status = entity.status,
            lineItems = lineItems.map { lineItemEntityToDomain(it) },
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt
        )
    }

    private fun lineItemEntityToDomain(entity: OrderLineItemEntity): OrderLineItem {
        return OrderLineItem(
            id = entity.id,
            orderId = entity.orderId,
            catalogItemId = entity.catalogItemId,
            sku = entity.sku,
            name = entity.name,
            unitPrice = entity.unitPrice,
            quantity = entity.quantity,
            lineTotal = entity.lineTotal,
            createdAt = entity.createdAt
        )
    }

    private fun orderToEntity(order: Order): OrderEntity {
        return OrderEntity(
            id = order.id,
            userId = order.userId,
            cartId = order.cartId,
            subtotal = order.subtotal,
            discountAmount = order.discountAmount,
            couponDiscount = order.couponDiscount,
            taxAmount = order.taxAmount,
            taxRate = order.taxRate,
            total = order.total,
            appliedCoupon = order.appliedCoupon,
            status = order.status,
            createdAt = order.createdAt,
            updatedAt = order.updatedAt
        )
    }

    private fun lineItemToEntity(item: OrderLineItem): OrderLineItemEntity {
        return OrderLineItemEntity(
            id = item.id,
            orderId = item.orderId,
            catalogItemId = item.catalogItemId,
            sku = item.sku,
            name = item.name,
            unitPrice = item.unitPrice,
            quantity = item.quantity,
            lineTotal = item.lineTotal,
            createdAt = item.createdAt
        )
    }
}
