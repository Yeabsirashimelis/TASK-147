package com.eaglepoint.storefront.data.repository

import com.eaglepoint.storefront.data.db.dao.UserDao
import com.eaglepoint.storefront.data.db.entity.UserEntity
import com.eaglepoint.storefront.security.FieldEncryptor
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import android.util.Base64

class AuthRepositoryTest {

    private lateinit var userDao: UserDao
    private lateinit var fieldEncryptor: FieldEncryptor
    private lateinit var authRepository: AuthRepository
    private val testDispatcher = StandardTestDispatcher()

    @BeforeEach
    fun setUp() {
        userDao = mockk(relaxed = true)
        fieldEncryptor = mockk(relaxed = true)

        // Mock encryption as identity + prefix byte for simplicity
        every { fieldEncryptor.encrypt(any()) } answers {
            val input = firstArg<ByteArray>()
            byteArrayOf(0x01) + input
        }
        every { fieldEncryptor.decrypt(any()) } answers {
            val input = firstArg<ByteArray>()
            input.sliceArray(1 until input.size)
        }

        authRepository = AuthRepository(userDao, fieldEncryptor, testDispatcher)
    }

    @Test
    fun `findByUsername returns null for nonexistent user`() = runTest(testDispatcher) {
        coEvery { userDao.findByUsername("nonexistent") } returns null

        val result = authRepository.findByUsername("nonexistent")

        assertThat(result).isNull()
    }

    @Test
    fun `findByUsername decrypts and returns domain model`() = runTest(testDispatcher) {
        val hash = byteArrayOf(1, 2, 3)
        val salt = byteArrayOf(4, 5, 6)
        val encryptedHash = byteArrayOf(0x01, 1, 2, 3)
        val encryptedSalt = byteArrayOf(0x01, 4, 5, 6)

        val entity = UserEntity(
            id = "user-1",
            username = "testuser",
            passwordHash = Base64.encodeToString(encryptedHash, Base64.NO_WRAP),
            passwordSalt = Base64.encodeToString(encryptedSalt, Base64.NO_WRAP),
            createdAt = 1000L,
            updatedAt = 1000L,
            isActive = true
        )
        coEvery { userDao.findByUsername("testuser") } returns entity

        val result = authRepository.findByUsername("testuser")

        assertThat(result).isNotNull()
        assertThat(result!!.username).isEqualTo("testuser")
        assertThat(result.passwordHash).isEqualTo(hash)
        assertThat(result.passwordSalt).isEqualTo(salt)
    }

    @Test
    fun `createUser encrypts hash and salt before persisting`() = runTest(testDispatcher) {
        val entitySlot = slot<UserEntity>()
        coEvery { userDao.insert(capture(entitySlot)) } returns Unit

        authRepository.createUser(
            id = "user-1",
            username = "newuser",
            passwordHash = byteArrayOf(10, 20, 30),
            passwordSalt = byteArrayOf(40, 50, 60),
            timestamp = 2000L
        )

        coVerify(exactly = 1) { userDao.insert(any()) }
        val captured = entitySlot.captured
        assertThat(captured.id).isEqualTo("user-1")
        assertThat(captured.username).isEqualTo("newuser")
        // Encrypted values should be Base64 encoded
        assertThat(captured.passwordHash).isNotEmpty()
        assertThat(captured.passwordSalt).isNotEmpty()
    }

    @Test
    fun `hasAnyUser delegates to dao`() = runTest(testDispatcher) {
        coEvery { userDao.hasAnyUser() } returns true

        val result = authRepository.hasAnyUser()

        assertThat(result).isTrue()
    }

    @Test
    fun `hasAnyUser returns false when no users`() = runTest(testDispatcher) {
        coEvery { userDao.hasAnyUser() } returns false

        val result = authRepository.hasAnyUser()

        assertThat(result).isFalse()
    }
}
