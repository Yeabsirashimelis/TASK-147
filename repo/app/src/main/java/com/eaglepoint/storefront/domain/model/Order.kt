package com.eaglepoint.storefront.domain.model

data class Order(
    val id: String,
    val userId: String,
    val cartId: String,
    val subtotal: Double,
    val discountAmount: Double = 0.0,
    val couponDiscount: Double = 0.0,
    val taxAmount: Double = 0.0,
    val taxRate: Double = 0.0,
    val total: Double,
    val appliedCoupon: String? = null,
    val status: String = "COMPLETED",
    val lineItems: List<OrderLineItem> = emptyList(),
    val createdAt: Long,
    val updatedAt: Long
)

data class OrderLineItem(
    val id: String,
    val orderId: String,
    val catalogItemId: String,
    val sku: String,
    val name: String,
    val unitPrice: Double,
    val quantity: Int,
    val lineTotal: Double,
    val createdAt: Long
)
