package com.eaglepoint.storefront.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "orders",
    indices = [
        Index(value = ["user_id"]),
        Index(value = ["created_at"])
    ]
)
data class OrderEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "cart_id") val cartId: String,
    val subtotal: Double,
    @ColumnInfo(name = "discount_amount") val discountAmount: Double = 0.0,
    @ColumnInfo(name = "coupon_discount") val couponDiscount: Double = 0.0,
    @ColumnInfo(name = "tax_amount") val taxAmount: Double = 0.0,
    @ColumnInfo(name = "tax_rate") val taxRate: Double = 0.0,
    val total: Double,
    @ColumnInfo(name = "applied_coupon") val appliedCoupon: String? = null,
    val status: String = "COMPLETED",
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long
)
