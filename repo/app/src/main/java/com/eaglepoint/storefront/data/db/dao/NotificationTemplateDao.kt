package com.eaglepoint.storefront.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.eaglepoint.storefront.data.db.entity.NotificationTemplateEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface NotificationTemplateDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(template: NotificationTemplateEntity)

    @Query("SELECT * FROM notification_templates WHERE event_type = :eventType AND is_active = 1 LIMIT 1")
    suspend fun findByEventType(eventType: String): NotificationTemplateEntity?

    @Query("SELECT * FROM notification_templates WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): NotificationTemplateEntity?

    @Query("SELECT * FROM notification_templates WHERE is_active = 1 ORDER BY name ASC")
    fun getAll(): Flow<List<NotificationTemplateEntity>>
}
