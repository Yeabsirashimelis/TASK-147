package com.eaglepoint.storefront.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.eaglepoint.storefront.data.db.entity.ValidationErrorEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ValidationErrorDao {

    @Insert
    suspend fun insertAll(errors: List<ValidationErrorEntity>)

    @Query("SELECT * FROM validation_errors WHERE batch_version_id = :batchVersionId ORDER BY created_at ASC")
    fun getByBatchVersion(batchVersionId: String): Flow<List<ValidationErrorEntity>>

    @Query("SELECT * FROM validation_errors WHERE batch_version_id = :batchVersionId ORDER BY created_at ASC")
    suspend fun getByBatchVersionList(batchVersionId: String): List<ValidationErrorEntity>

    @Query("SELECT COUNT(*) FROM validation_errors WHERE batch_version_id = :batchVersionId")
    suspend fun countByBatchVersion(batchVersionId: String): Int

    @Query("SELECT rule_name, COUNT(*) as count FROM validation_errors WHERE batch_version_id = :batchVersionId GROUP BY rule_name")
    suspend fun countByRule(batchVersionId: String): List<RuleErrorCount>

    @Query("DELETE FROM validation_errors WHERE batch_version_id = :batchVersionId")
    suspend fun deleteByBatchVersion(batchVersionId: String)
}

data class RuleErrorCount(
    val rule_name: String,
    val count: Int
)
