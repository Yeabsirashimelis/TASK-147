package com.eaglepoint.storefront.security

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

class PasswordHasher(
    private val iterations: Int = DEFAULT_ITERATIONS,
    private val keyLengthBits: Int = DEFAULT_KEY_LENGTH_BITS,
    private val saltLengthBytes: Int = DEFAULT_SALT_LENGTH_BYTES
) {

    fun generateSalt(): ByteArray {
        val salt = ByteArray(saltLengthBytes)
        SecureRandom().nextBytes(salt)
        return salt
    }

    fun hash(password: CharArray, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(password, salt, iterations, keyLengthBits)
        return try {
            val factory = SecretKeyFactory.getInstance(ALGORITHM)
            factory.generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    fun verify(password: CharArray, salt: ByteArray, expectedHash: ByteArray): Boolean {
        val actualHash = hash(password, salt)
        return MessageDigest.isEqual(actualHash, expectedHash)
    }

    companion object {
        private const val ALGORITHM = "PBKDF2WithHmacSHA512"
        private const val DEFAULT_ITERATIONS = 210_000
        private const val DEFAULT_KEY_LENGTH_BITS = 256
        private const val DEFAULT_SALT_LENGTH_BYTES = 32
    }
}
