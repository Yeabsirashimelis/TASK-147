package com.eaglepoint.storefront.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.eaglepoint.storefront.data.db.entity.DataLineageEntity

@Dao
interface DataLineageDao {

    @Insert
    suspend fun insert(lineage: DataLineageEntity)

    @Insert
    suspend fun insertAll(lineages: List<DataLineageEntity>)

    @Query("SELECT * FROM data_lineage WHERE target_entity = :entity AND target_id = :id")
    suspend fun getSourcesFor(entity: String, id: String): List<DataLineageEntity>

    @Query("SELECT * FROM data_lineage WHERE source_entity = :entity AND source_id = :id")
    suspend fun getTargetsFor(entity: String, id: String): List<DataLineageEntity>

    @Query("SELECT * FROM data_lineage WHERE batch_version_id = :batchVersionId")
    suspend fun getByBatchVersion(batchVersionId: String): List<DataLineageEntity>
}
