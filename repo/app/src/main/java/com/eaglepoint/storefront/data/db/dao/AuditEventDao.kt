package com.eaglepoint.storefront.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.RawQuery
import androidx.sqlite.db.SupportSQLiteQuery
import com.eaglepoint.storefront.data.db.entity.AuditEventEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AuditEventDao {

    @Insert
    suspend fun insert(event: AuditEventEntity)

    @Query("SELECT * FROM audit_events ORDER BY timestamp DESC LIMIT :limit OFFSET :offset")
    fun getPage(limit: Int, offset: Int): Flow<List<AuditEventEntity>>

    @Query("SELECT * FROM audit_events WHERE action = :action ORDER BY timestamp DESC")
    fun getByAction(action: String): Flow<List<AuditEventEntity>>

    @Query("SELECT * FROM audit_events WHERE timestamp BETWEEN :from AND :to ORDER BY timestamp DESC")
    fun getByDateRange(from: Long, to: Long): Flow<List<AuditEventEntity>>

    @Query("SELECT COUNT(*) FROM audit_events")
    suspend fun count(): Int

    @RawQuery
    suspend fun checkpoint(query: SupportSQLiteQuery): Int
}
