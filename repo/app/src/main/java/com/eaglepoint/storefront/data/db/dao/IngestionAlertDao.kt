package com.eaglepoint.storefront.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.eaglepoint.storefront.data.db.entity.IngestionAlertEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface IngestionAlertDao {

    @Insert
    suspend fun insert(alert: IngestionAlertEntity)

    @Query("SELECT * FROM ingestion_alerts WHERE is_acknowledged = 0 ORDER BY created_at DESC")
    fun getUnacknowledged(): Flow<List<IngestionAlertEntity>>

    @Query("SELECT * FROM ingestion_alerts ORDER BY created_at DESC LIMIT :limit OFFSET :offset")
    fun getAll(limit: Int, offset: Int): Flow<List<IngestionAlertEntity>>

    @Query("UPDATE ingestion_alerts SET is_acknowledged = 1 WHERE id = :id")
    suspend fun acknowledge(id: String)

    @Query("SELECT COUNT(*) FROM ingestion_alerts WHERE is_acknowledged = 0")
    fun getUnacknowledgedCount(): Flow<Int>

    @Query("SELECT EXISTS(SELECT 1 FROM ingestion_alerts WHERE source_rule_id = :sourceRuleId AND is_acknowledged = 0)")
    suspend fun hasUnacknowledgedForSource(sourceRuleId: String): Boolean
}
