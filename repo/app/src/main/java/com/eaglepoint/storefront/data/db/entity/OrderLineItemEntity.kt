package com.eaglepoint.storefront.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "order_line_items",
    foreignKeys = [
        ForeignKey(
            entity = OrderEntity::class,
            parentColumns = ["id"],
            childColumns = ["order_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["order_id"]),
        Index(value = ["order_id", "sku"], unique = true)
    ]
)
data class OrderLineItemEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "order_id") val orderId: String,
    @ColumnInfo(name = "catalog_item_id") val catalogItemId: String,
    val sku: String,
    val name: String,
    @ColumnInfo(name = "unit_price") val unitPrice: Double,
    val quantity: Int,
    @ColumnInfo(name = "line_total") val lineTotal: Double,
    @ColumnInfo(name = "created_at") val createdAt: Long
)
