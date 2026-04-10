package com.eaglepoint.storefront.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.eaglepoint.storefront.data.db.entity.SourceRuleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SourceRuleDao {

    @Query("SELECT * FROM source_rules WHERE is_active = 1 ORDER BY name ASC")
    fun getAllActive(): Flow<List<SourceRuleEntity>>

    @Query("SELECT * FROM source_rules ORDER BY name ASC")
    fun getAll(): Flow<List<SourceRuleEntity>>

    @Query("SELECT * FROM source_rules WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): SourceRuleEntity?

    @Query("SELECT * FROM source_rules WHERE name = :name LIMIT 1")
    suspend fun findByName(name: String): SourceRuleEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(rule: SourceRuleEntity)

    @Update
    suspend fun update(rule: SourceRuleEntity)

    @Query("UPDATE source_rules SET is_active = 0, updated_at = :timestamp WHERE id = :id")
    suspend fun deactivate(id: String, timestamp: Long)

    @Query("SELECT * FROM source_rules WHERE is_active = 1")
    suspend fun getAllActiveList(): List<SourceRuleEntity>
}
