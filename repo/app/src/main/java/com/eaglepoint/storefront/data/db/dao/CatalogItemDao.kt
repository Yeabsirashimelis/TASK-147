package com.eaglepoint.storefront.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.eaglepoint.storefront.data.db.entity.CatalogItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CatalogItemDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: CatalogItemEntity)

    @Query("SELECT * FROM catalog_items WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): CatalogItemEntity?

    @Query("SELECT * FROM catalog_items WHERE sku = :sku LIMIT 1")
    suspend fun findBySku(sku: String): CatalogItemEntity?

    @Query("SELECT * FROM catalog_items WHERE is_active = 1 ORDER BY name ASC")
    fun getActive(): Flow<List<CatalogItemEntity>>

    @Query("SELECT * FROM catalog_items WHERE is_active = 1 ORDER BY name ASC LIMIT :limit OFFSET :offset")
    fun getActivePaged(limit: Int, offset: Int): Flow<List<CatalogItemEntity>>

    @Query("SELECT * FROM catalog_items WHERE batch_version_id = :batchVersionId")
    suspend fun getByBatchVersionId(batchVersionId: String): List<CatalogItemEntity>
}
