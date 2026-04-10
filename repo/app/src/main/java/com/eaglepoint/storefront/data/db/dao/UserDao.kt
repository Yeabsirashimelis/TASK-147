package com.eaglepoint.storefront.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.eaglepoint.storefront.data.db.entity.UserEntity

@Dao
interface UserDao {

    @Query("SELECT * FROM users WHERE username = :username AND is_active = 1 LIMIT 1")
    suspend fun findByUsername(username: String): UserEntity?

    @Query("SELECT * FROM users WHERE id = :id AND is_active = 1 LIMIT 1")
    suspend fun findById(id: String): UserEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(user: UserEntity)

    @Query("SELECT EXISTS(SELECT 1 FROM users LIMIT 1)")
    suspend fun hasAnyUser(): Boolean

    @Query("UPDATE users SET is_active = 0, updated_at = :timestamp WHERE id = :userId")
    suspend fun deactivate(userId: String, timestamp: Long)
}
