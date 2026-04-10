package com.eaglepoint.storefront.security

import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec

class FieldEncryptor(private val keystoreManager: KeystoreManager) {

    fun encrypt(plaintext: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, keystoreManager.getOrCreateKey())
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(plaintext)
        // Prepend IV to ciphertext: [IV_LENGTH(1) | IV | ciphertext+tag]
        return byteArrayOf(iv.size.toByte()) + iv + ciphertext
    }

    fun decrypt(data: ByteArray): ByteArray {
        val ivLength = data[0].toInt() and 0xFF
        val iv = data.sliceArray(1..ivLength)
        val ciphertext = data.sliceArray((1 + ivLength) until data.size)

        val cipher = Cipher.getInstance(TRANSFORMATION)
        val spec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
        cipher.init(Cipher.DECRYPT_MODE, keystoreManager.getOrCreateKey(), spec)
        return cipher.doFinal(ciphertext)
    }

    companion object {
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_LENGTH_BITS = 128
    }
}
