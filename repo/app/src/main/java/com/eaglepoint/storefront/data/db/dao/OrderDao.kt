package com.eaglepoint.storefront.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.eaglepoint.storefront.data.db.entity.OrderEntity
import com.eaglepoint.storefront.data.db.entity.OrderLineItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface OrderDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrder(order: OrderEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLineItems(items: List<OrderLineItemEntity>)

    @Query("SELECT * FROM orders WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): OrderEntity?

    @Query("SELECT * FROM orders WHERE user_id = :userId ORDER BY created_at DESC")
    fun getByUserId(userId: String): Flow<List<OrderEntity>>

    @Query("SELECT * FROM orders WHERE user_id = :userId ORDER BY created_at DESC")
    suspend fun getByUserIdList(userId: String): List<OrderEntity>

    @Query("SELECT * FROM order_line_items WHERE order_id = :orderId")
    suspend fun getLineItemsByOrderId(orderId: String): List<OrderLineItemEntity>

    @Query("SELECT * FROM order_line_items WHERE order_id = :orderId")
    fun observeLineItems(orderId: String): Flow<List<OrderLineItemEntity>>
}
