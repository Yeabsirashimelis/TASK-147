package com.eaglepoint.storefront.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.eaglepoint.storefront.data.db.entity.InventorySnapshotEntity

@Dao
interface InventorySnapshotDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(snapshot: InventorySnapshotEntity)

    @Query("SELECT * FROM inventory_snapshots WHERE catalog_item_id = :catalogItemId ORDER BY created_at DESC LIMIT 1")
    suspend fun getLatestForItem(catalogItemId: String): InventorySnapshotEntity?

    @Query("SELECT quantity - reserved_quantity FROM inventory_snapshots WHERE catalog_item_id = :catalogItemId ORDER BY created_at DESC LIMIT 1")
    suspend fun getAvailableQuantity(catalogItemId: String): Int?

    @Query("SELECT * FROM inventory_snapshots WHERE batch_version_id = :batchVersionId")
    suspend fun getByBatchVersionId(batchVersionId: String): List<InventorySnapshotEntity>
}
