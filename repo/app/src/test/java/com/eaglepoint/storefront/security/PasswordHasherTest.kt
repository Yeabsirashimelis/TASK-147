package com.eaglepoint.storefront.security

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class PasswordHasherTest {

    private lateinit var hasher: PasswordHasher

    @BeforeEach
    fun setUp() {
        // Use lower iterations for test speed
        hasher = PasswordHasher(iterations = 1000)
    }

    @Test
    fun `generateSalt returns 32 bytes`() {
        val salt = hasher.generateSalt()
        assertThat(salt).hasLength(32)
    }

    @Test
    fun `generateSalt produces unique salts`() {
        val salt1 = hasher.generateSalt()
        val salt2 = hasher.generateSalt()
        assertThat(salt1).isNotEqualTo(salt2)
    }

    @Test
    fun `hash produces deterministic output for same password and salt`() {
        val password = "TestPassword1".toCharArray()
        val salt = hasher.generateSalt()

        val hash1 = hasher.hash(password.copyOf(), salt)
        val hash2 = hasher.hash(password.copyOf(), salt)

        assertThat(hash1).isEqualTo(hash2)
    }

    @Test
    fun `hash produces different output for different salts`() {
        val password = "TestPassword1".toCharArray()
        val salt1 = hasher.generateSalt()
        val salt2 = hasher.generateSalt()

        val hash1 = hasher.hash(password.copyOf(), salt1)
        val hash2 = hasher.hash(password.copyOf(), salt2)

        assertThat(hash1).isNotEqualTo(hash2)
    }

    @Test
    fun `hash produces different output for different passwords`() {
        val salt = hasher.generateSalt()

        val hash1 = hasher.hash("Password1".toCharArray(), salt)
        val hash2 = hasher.hash("Password2".toCharArray(), salt)

        assertThat(hash1).isNotEqualTo(hash2)
    }

    @Test
    fun `verify returns true for correct password`() {
        val password = "CorrectPassword1".toCharArray()
        val salt = hasher.generateSalt()
        val hash = hasher.hash(password.copyOf(), salt)

        val result = hasher.verify(password.copyOf(), salt, hash)

        assertThat(result).isTrue()
    }

    @Test
    fun `verify returns false for wrong password`() {
        val salt = hasher.generateSalt()
        val hash = hasher.hash("CorrectPassword1".toCharArray(), salt)

        val result = hasher.verify("WrongPassword1".toCharArray(), salt, hash)

        assertThat(result).isFalse()
    }

    @Test
    fun `hash output has expected key length`() {
        val password = "TestPassword1".toCharArray()
        val salt = hasher.generateSalt()

        val hash = hasher.hash(password, salt)

        // 256 bits = 32 bytes
        assertThat(hash).hasLength(32)
    }
}
