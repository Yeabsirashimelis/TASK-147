package com.eaglepoint.storefront.data.repository

import android.util.Base64
import com.eaglepoint.storefront.data.db.dao.UserDao
import com.eaglepoint.storefront.data.db.entity.UserEntity
import com.eaglepoint.storefront.domain.model.User
import com.eaglepoint.storefront.domain.model.UserRole
import com.eaglepoint.storefront.security.FieldEncryptor
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AuthRepository(
    private val userDao: UserDao,
    private val fieldEncryptor: FieldEncryptor,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    suspend fun findByUsername(username: String): User? = withContext(dispatcher) {
        val entity = userDao.findByUsername(username) ?: return@withContext null
        entityToDomain(entity)
    }

    suspend fun findById(id: String): User? = withContext(dispatcher) {
        val entity = userDao.findById(id) ?: return@withContext null
        entityToDomain(entity)
    }

    suspend fun createUser(
        id: String,
        username: String,
        passwordHash: ByteArray,
        passwordSalt: ByteArray,
        timestamp: Long,
        role: String = "USER"
    ) = withContext(dispatcher) {
        val encryptedHash = fieldEncryptor.encrypt(passwordHash)
        val encryptedSalt = fieldEncryptor.encrypt(passwordSalt)

        val entity = UserEntity(
            id = id,
            username = username,
            passwordHash = Base64.encodeToString(encryptedHash, Base64.NO_WRAP),
            passwordSalt = Base64.encodeToString(encryptedSalt, Base64.NO_WRAP),
            createdAt = timestamp,
            updatedAt = timestamp,
            role = role,
            isActive = true
        )
        userDao.insert(entity)
    }

    suspend fun hasAnyUser(): Boolean = withContext(dispatcher) {
        userDao.hasAnyUser()
    }

    suspend fun deactivateUser(userId: String, timestamp: Long) = withContext(dispatcher) {
        userDao.deactivate(userId, timestamp)
    }

    private fun entityToDomain(entity: UserEntity): User {
        val decryptedHash = fieldEncryptor.decrypt(
            Base64.decode(entity.passwordHash, Base64.NO_WRAP)
        )
        val decryptedSalt = fieldEncryptor.decrypt(
            Base64.decode(entity.passwordSalt, Base64.NO_WRAP)
        )
        return User(
            id = entity.id,
            username = entity.username,
            passwordHash = decryptedHash,
            passwordSalt = decryptedSalt,
            role = try { UserRole.valueOf(entity.role) } catch (_: Exception) { UserRole.USER },
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt,
            isActive = entity.isActive
        )
    }
}
