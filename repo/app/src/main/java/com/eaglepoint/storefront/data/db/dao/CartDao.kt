package com.eaglepoint.storefront.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.eaglepoint.storefront.data.db.entity.CartEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CartDao {

    @Insert
    suspend fun insert(cart: CartEntity)

    @Update
    suspend fun update(cart: CartEntity)

    @Query("SELECT * FROM carts WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): CartEntity?

    @Query("SELECT * FROM carts WHERE user_id = :userId AND status = 'ACTIVE' LIMIT 1")
    suspend fun findActiveByUserId(userId: String): CartEntity?

    @Query("SELECT * FROM carts WHERE user_id IS NULL AND status = 'ACTIVE' AND id = :cartId LIMIT 1")
    suspend fun findGuestCart(cartId: String): CartEntity?

    @Query("SELECT * FROM carts WHERE id = :id LIMIT 1")
    fun observeById(id: String): Flow<CartEntity?>

    @Query("UPDATE carts SET status = :status, updated_at = :updatedAt WHERE id = :id")
    suspend fun updateStatus(id: String, status: String, updatedAt: Long)

    @Query("UPDATE carts SET user_id = :userId, updated_at = :updatedAt WHERE id = :id")
    suspend fun assignUser(id: String, userId: String, updatedAt: Long)

    @Query("UPDATE carts SET coupon_id = :couponId, updated_at = :updatedAt WHERE id = :id")
    suspend fun setCoupon(id: String, couponId: String?, updatedAt: Long)

    @Query("UPDATE carts SET price_locked_at = :lockedAt, price_lock_expires_at = :expiresAt, updated_at = :updatedAt WHERE id = :id")
    suspend fun setPriceLock(id: String, lockedAt: Long?, expiresAt: Long?, updatedAt: Long)

    @Query("DELETE FROM carts WHERE id = :id")
    suspend fun delete(id: String)
}
