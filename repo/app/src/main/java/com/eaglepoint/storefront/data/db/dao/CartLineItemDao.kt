package com.eaglepoint.storefront.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.eaglepoint.storefront.data.db.entity.CartLineItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CartLineItemDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: CartLineItemEntity)

    @Insert
    suspend fun insertAll(items: List<CartLineItemEntity>)

    @Update
    suspend fun update(item: CartLineItemEntity)

    @Query("SELECT * FROM cart_line_items WHERE cart_id = :cartId ORDER BY created_at ASC")
    fun getByCartId(cartId: String): Flow<List<CartLineItemEntity>>

    @Query("SELECT * FROM cart_line_items WHERE cart_id = :cartId ORDER BY created_at ASC")
    suspend fun getByCartIdList(cartId: String): List<CartLineItemEntity>

    @Query("SELECT * FROM cart_line_items WHERE cart_id = :cartId AND sku = :sku LIMIT 1")
    suspend fun findByCartAndSku(cartId: String, sku: String): CartLineItemEntity?

    @Query("SELECT * FROM cart_line_items WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): CartLineItemEntity?

    @Query("UPDATE cart_line_items SET quantity = :quantity, updated_at = :updatedAt WHERE id = :id")
    suspend fun updateQuantity(id: String, quantity: Int, updatedAt: Long)

    @Query("UPDATE cart_line_items SET locked_price = :lockedPrice, updated_at = :updatedAt WHERE cart_id = :cartId")
    suspend fun lockPrices(cartId: String, lockedPrice: Double?, updatedAt: Long)

    @Query("DELETE FROM cart_line_items WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM cart_line_items WHERE cart_id = :cartId")
    suspend fun deleteAllForCart(cartId: String)

    @Query("SELECT COUNT(*) FROM cart_line_items WHERE cart_id = :cartId")
    suspend fun countByCart(cartId: String): Int

    @Query("SELECT SUM(quantity) FROM cart_line_items WHERE cart_id = :cartId")
    suspend fun totalQuantityForCart(cartId: String): Int?
}
