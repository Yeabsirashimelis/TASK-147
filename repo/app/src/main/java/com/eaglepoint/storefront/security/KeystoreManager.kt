package com.eaglepoint.storefront.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

class KeystoreManager {

    private val keyStore: KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply {
        load(null)
    }

    fun getOrCreateKey(): SecretKey {
        val existingEntry = keyStore.getEntry(KEY_ALIAS, null)
        if (existingEntry is KeyStore.SecretKeyEntry) {
            return existingEntry.secretKey
        }
        return generateKey()
    }

    private fun generateKey(): SecretKey {
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(KEY_SIZE_BITS)
            .build()

        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE
        )
        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    companion object {
        private const val KEY_ALIAS = "storefront_master_key"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_SIZE_BITS = 256
    }
}
