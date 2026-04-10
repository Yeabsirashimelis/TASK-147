package com.eaglepoint.storefront.security

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class SensitiveFieldMaskerTest {

    @Test
    fun `maskHash returns masked string for non-empty hash`() {
        val hash = byteArrayOf(0xAB.toByte(), 0xCD.toByte(), 0xEF.toByte(), 0x12)

        val masked = SensitiveFieldMasker.maskHash(hash)

        assertThat(masked).isEqualTo("abcd...")
    }

    @Test
    fun `maskHash returns stars for empty hash`() {
        val masked = SensitiveFieldMasker.maskHash(byteArrayOf())
        assertThat(masked).isEqualTo("****")
    }

    @Test
    fun `maskId preserves prefix and suffix only`() {
        val id = "550e8400-e29b-41d4-a716-446655440000"

        val masked = SensitiveFieldMasker.maskId(id)

        assertThat(masked).isEqualTo("550e****00")
    }

    @Test
    fun `maskId returns stars for short id`() {
        val masked = SensitiveFieldMasker.maskId("abc")
        assertThat(masked).isEqualTo("****")
    }

    @Test
    fun `maskForLog masks known sensitive keys`() {
        assertThat(SensitiveFieldMasker.maskForLog("passwordHash", "secret_value")).isEqualTo("****")
        assertThat(SensitiveFieldMasker.maskForLog("passwordSalt", byteArrayOf())).isEqualTo("****")
        assertThat(SensitiveFieldMasker.maskForLog("password", "mypassword")).isEqualTo("****")
        assertThat(SensitiveFieldMasker.maskForLog("userToken", "abc123")).isEqualTo("****")
    }

    @Test
    fun `maskForLog passes through non-sensitive keys`() {
        assertThat(SensitiveFieldMasker.maskForLog("username", "john")).isEqualTo("john")
        assertThat(SensitiveFieldMasker.maskForLog("action", "LOGIN")).isEqualTo("LOGIN")
    }

    @Test
    fun `maskForLog returns null string for null value`() {
        assertThat(SensitiveFieldMasker.maskForLog("anything", null)).isEqualTo("null")
    }

    @Test
    fun `isSensitive is case insensitive`() {
        assertThat(SensitiveFieldMasker.isSensitive("PasswordHash")).isTrue()
        assertThat(SensitiveFieldMasker.isSensitive("PASSWORDHASH")).isTrue()
        assertThat(SensitiveFieldMasker.isSensitive("user_password_field")).isTrue()
    }

    @Test
    fun `maskDetailMap masks sensitive keys in map`() {
        val details = mapOf(
            "username" to "john",
            "passwordHash" to "abc123",
            "action" to "LOGIN"
        )

        val masked = SensitiveFieldMasker.maskDetailMap(details)

        assertThat(masked["username"]).isEqualTo("john")
        assertThat(masked["passwordHash"]).isEqualTo("****")
        assertThat(masked["action"]).isEqualTo("LOGIN")
    }

    @Test
    fun `maskSensitiveContent masks inline password values`() {
        val text = "User changed password: newPass123 for account"
        val masked = SensitiveFieldMasker.maskSensitiveContent(text)
        assertThat(masked).doesNotContain("newPass123")
    }

    @Test
    fun `maskSensitiveContent masks token values`() {
        val text = "token=abc123xyz"
        val masked = SensitiveFieldMasker.maskSensitiveContent(text)
        assertThat(masked).doesNotContain("abc123xyz")
    }

    @Test
    fun `maskAuditDetail returns null for null input`() {
        assertThat(SensitiveFieldMasker.maskAuditDetail(null)).isNull()
    }

    @Test
    fun `maskAuditDetail passes through safe text unchanged`() {
        val safe = "User logged in successfully"
        assertThat(SensitiveFieldMasker.maskAuditDetail(safe)).isEqualTo(safe)
    }

    @Test
    fun `isSensitive detects credential key`() {
        assertThat(SensitiveFieldMasker.isSensitive("userCredential")).isTrue()
        assertThat(SensitiveFieldMasker.isSensitive("apiKey")).isTrue()
    }
}
