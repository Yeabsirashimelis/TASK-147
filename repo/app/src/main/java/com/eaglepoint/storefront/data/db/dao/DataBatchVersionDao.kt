package com.eaglepoint.storefront.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.eaglepoint.storefront.data.db.entity.DataBatchVersionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DataBatchVersionDao {

    @Insert
    suspend fun insert(batch: DataBatchVersionEntity)

    @Update
    suspend fun update(batch: DataBatchVersionEntity)

    @Query("SELECT * FROM data_batch_versions WHERE source_rule_id = :sourceRuleId ORDER BY version DESC LIMIT 1")
    suspend fun getLatestForSource(sourceRuleId: String): DataBatchVersionEntity?

    @Query("SELECT MAX(version) FROM data_batch_versions WHERE source_rule_id = :sourceRuleId AND batch_name = :batchName")
    suspend fun getMaxVersion(sourceRuleId: String, batchName: String): Int?

    @Query("SELECT * FROM data_batch_versions WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): DataBatchVersionEntity?

    @Query("SELECT * FROM data_batch_versions ORDER BY created_at DESC LIMIT :limit OFFSET :offset")
    fun getRecent(limit: Int, offset: Int): Flow<List<DataBatchVersionEntity>>

    @Query("SELECT * FROM data_batch_versions WHERE validation_status = :status ORDER BY created_at DESC")
    fun getByValidationStatus(status: String): Flow<List<DataBatchVersionEntity>>

    @Query("SELECT * FROM data_batch_versions WHERE validation_status = 'FAILED' ORDER BY created_at DESC LIMIT :limit")
    fun getRecentFailed(limit: Int): Flow<List<DataBatchVersionEntity>>
}
