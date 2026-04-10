package com.eaglepoint.storefront.security

import java.io.File
import java.io.InputStream
import java.security.MessageDigest

object ChecksumUtil {

    private const val ALGORITHM = "SHA-256"
    private const val BUFFER_SIZE = 8192

    fun computeSha256(file: File): String {
        return file.inputStream().use { computeSha256(it) }
    }

    fun computeSha256(inputStream: InputStream): String {
        val digest = MessageDigest.getInstance(ALGORITHM)
        val buffer = ByteArray(BUFFER_SIZE)
        var bytesRead: Int
        while (inputStream.read(buffer).also { bytesRead = it } != -1) {
            digest.update(buffer, 0, bytesRead)
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    fun verify(file: File, expectedChecksum: String): Boolean {
        val actual = computeSha256(file)
        return actual.equals(expectedChecksum, ignoreCase = true)
    }
}
