package com.eaglepoint.storefront.security

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

class ChecksumUtilTest {

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `computeSha256 produces correct hash for known input`() {
        val file = File(tempDir.toFile(), "test.txt")
        file.writeText("hello world")

        val hash = ChecksumUtil.computeSha256(file)

        // Known SHA-256 of "hello world"
        assertThat(hash).isEqualTo("b94d27b9934d3e08a52e52d7da7dabfac484efe37a5380ee9088f7ace2efcde9")
    }

    @Test
    fun `computeSha256 from input stream matches file hash`() {
        val file = File(tempDir.toFile(), "test.txt")
        file.writeText("test content for checksumming")

        val fileHash = ChecksumUtil.computeSha256(file)
        val streamHash = file.inputStream().use { ChecksumUtil.computeSha256(it) }

        assertThat(fileHash).isEqualTo(streamHash)
    }

    @Test
    fun `verify returns true for matching checksum`() {
        val file = File(tempDir.toFile(), "test.txt")
        file.writeText("verify me")

        val checksum = ChecksumUtil.computeSha256(file)
        val result = ChecksumUtil.verify(file, checksum)

        assertThat(result).isTrue()
    }

    @Test
    fun `verify returns false for tampered file`() {
        val file = File(tempDir.toFile(), "test.txt")
        file.writeText("original content")
        val checksum = ChecksumUtil.computeSha256(file)

        // Tamper the file
        file.writeText("tampered content")

        val result = ChecksumUtil.verify(file, checksum)
        assertThat(result).isFalse()
    }

    @Test
    fun `verify is case insensitive for checksum comparison`() {
        val file = File(tempDir.toFile(), "test.txt")
        file.writeText("case test")

        val checksum = ChecksumUtil.computeSha256(file)
        val result = ChecksumUtil.verify(file, checksum.uppercase())

        assertThat(result).isTrue()
    }

    @Test
    fun `different files produce different checksums`() {
        val file1 = File(tempDir.toFile(), "file1.txt")
        val file2 = File(tempDir.toFile(), "file2.txt")
        file1.writeText("content A")
        file2.writeText("content B")

        val hash1 = ChecksumUtil.computeSha256(file1)
        val hash2 = ChecksumUtil.computeSha256(file2)

        assertThat(hash1).isNotEqualTo(hash2)
    }
}
