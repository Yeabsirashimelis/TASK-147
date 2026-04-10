package com.eaglepoint.storefront.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.eaglepoint.storefront.data.db.entity.CouponEntity

@Dao
interface CouponDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(coupon: CouponEntity)

    @Query("SELECT * FROM coupons WHERE code = :code AND is_active = 1 LIMIT 1")
    suspend fun findByCode(code: String): CouponEntity?

    @Query("SELECT * FROM coupons WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): CouponEntity?

    @Query("UPDATE coupons SET current_uses = current_uses + 1, updated_at = :updatedAt WHERE id = :id")
    suspend fun incrementUsage(id: String, updatedAt: Long)
}
