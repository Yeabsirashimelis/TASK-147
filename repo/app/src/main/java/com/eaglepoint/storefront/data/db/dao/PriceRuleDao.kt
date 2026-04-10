package com.eaglepoint.storefront.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.eaglepoint.storefront.data.db.entity.PriceRuleEntity

@Dao
interface PriceRuleDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(rule: PriceRuleEntity)

    @Query("SELECT * FROM price_rules WHERE is_active = 1 ORDER BY priority DESC")
    suspend fun getActiveRules(): List<PriceRuleEntity>

    @Query("SELECT * FROM price_rules WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): PriceRuleEntity?
}
