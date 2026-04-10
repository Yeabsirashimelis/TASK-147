package com.eaglepoint.storefront.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.eaglepoint.storefront.data.db.entity.TaxRateEntity

@Dao
interface TaxRateDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(rate: TaxRateEntity)

    @Query("SELECT * FROM tax_rates WHERE state_code = :stateCode AND is_active = 1 LIMIT 1")
    suspend fun findByStateCode(stateCode: String): TaxRateEntity?

    @Query("SELECT * FROM tax_rates WHERE is_active = 1 ORDER BY state_name ASC")
    suspend fun getAll(): List<TaxRateEntity>
}
