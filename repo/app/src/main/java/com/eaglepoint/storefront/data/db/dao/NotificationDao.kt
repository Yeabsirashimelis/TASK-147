package com.eaglepoint.storefront.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.eaglepoint.storefront.data.db.entity.NotificationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface NotificationDao {

    @Insert
    suspend fun insert(notification: NotificationEntity)

    @Update
    suspend fun update(notification: NotificationEntity)

    @Query("SELECT * FROM notifications WHERE recipient_id = :recipientId ORDER BY created_at DESC LIMIT :limit OFFSET :offset")
    fun getByRecipient(recipientId: String, limit: Int, offset: Int): Flow<List<NotificationEntity>>

    @Query("SELECT * FROM notifications WHERE recipient_id = :recipientId AND is_read = 0 ORDER BY created_at DESC")
    fun getUnreadByRecipient(recipientId: String): Flow<List<NotificationEntity>>

    @Query("SELECT COUNT(*) FROM notifications WHERE recipient_id = :recipientId AND is_read = 0")
    fun getUnreadCount(recipientId: String): Flow<Int>

    @Query("SELECT * FROM notifications WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): NotificationEntity?

    @Query("UPDATE notifications SET is_read = 1, updated_at = :updatedAt WHERE id = :id")
    suspend fun markRead(id: String, updatedAt: Long)

    @Query("UPDATE notifications SET is_read = 1, updated_at = :updatedAt WHERE recipient_id = :recipientId")
    suspend fun markAllRead(recipientId: String, updatedAt: Long)

    @Query("SELECT * FROM notifications WHERE status = 'PENDING' OR (status = 'FAILED' AND retry_count < max_retries) ORDER BY created_at ASC")
    suspend fun getPendingDeliveries(): List<NotificationEntity>

    @Query("UPDATE notifications SET status = :status, retry_count = :retryCount, failure_reason = :failureReason, delivered_at = :deliveredAt, updated_at = :updatedAt WHERE id = :id")
    suspend fun updateDeliveryStatus(id: String, status: String, retryCount: Int, failureReason: String?, deliveredAt: Long?, updatedAt: Long)
}
