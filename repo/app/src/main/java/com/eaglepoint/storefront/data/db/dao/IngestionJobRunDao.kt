package com.eaglepoint.storefront.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.eaglepoint.storefront.data.db.entity.IngestionJobRunEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface IngestionJobRunDao {

    @Insert
    suspend fun insert(run: IngestionJobRunEntity)

    @Update
    suspend fun update(run: IngestionJobRunEntity)

    @Query("SELECT * FROM ingestion_job_runs WHERE source_rule_id = :sourceRuleId ORDER BY started_at DESC LIMIT :limit OFFSET :offset")
    fun getBySourceRule(sourceRuleId: String, limit: Int, offset: Int): Flow<List<IngestionJobRunEntity>>

    @Query("SELECT * FROM ingestion_job_runs ORDER BY started_at DESC LIMIT :limit OFFSET :offset")
    fun getAll(limit: Int, offset: Int): Flow<List<IngestionJobRunEntity>>

    @Query("SELECT * FROM ingestion_job_runs WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): IngestionJobRunEntity?

    @Query("SELECT COUNT(*) FROM ingestion_job_runs WHERE source_rule_id = :sourceRuleId AND status = 'FAILURE' AND started_at > :since")
    suspend fun countFailuresSince(sourceRuleId: String, since: Long): Int

    @Query("SELECT * FROM ingestion_job_runs WHERE source_rule_id = :sourceRuleId ORDER BY started_at DESC LIMIT 1")
    suspend fun getLatestForSource(sourceRuleId: String): IngestionJobRunEntity?
}
