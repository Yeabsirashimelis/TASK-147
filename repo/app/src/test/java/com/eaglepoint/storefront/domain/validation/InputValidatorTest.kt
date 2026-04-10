package com.eaglepoint.storefront.domain.validation

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class InputValidatorTest {

    private lateinit var validator: InputValidator

    @BeforeEach
    fun setUp() {
        validator = InputValidator()
    }

    // Username validation

    @Test
    fun `valid username accepted`() {
        assertThat(validator.validateUsername("john_doe")).isInstanceOf(ValidationResult.Valid::class.java)
    }

    @Test
    fun `username with minimum length accepted`() {
        assertThat(validator.validateUsername("abc")).isInstanceOf(ValidationResult.Valid::class.java)
    }

    @Test
    fun `username too short rejected`() {
        val result = validator.validateUsername("ab")
        assertThat(result).isInstanceOf(ValidationResult.Invalid::class.java)
    }

    @Test
    fun `username too long rejected`() {
        val longName = "a".repeat(65)
        val result = validator.validateUsername(longName)
        assertThat(result).isInstanceOf(ValidationResult.Invalid::class.java)
    }

    @Test
    fun `username with special characters rejected`() {
        assertThat(validator.validateUsername("john@doe")).isInstanceOf(ValidationResult.Invalid::class.java)
        assertThat(validator.validateUsername("john doe")).isInstanceOf(ValidationResult.Invalid::class.java)
        assertThat(validator.validateUsername("john-doe")).isInstanceOf(ValidationResult.Invalid::class.java)
    }

    @Test
    fun `SQL injection in username rejected`() {
        assertThat(validator.validateUsername("' OR 1=1 --")).isInstanceOf(ValidationResult.Invalid::class.java)
        assertThat(validator.validateUsername("admin'; DROP TABLE users;--")).isInstanceOf(ValidationResult.Invalid::class.java)
        assertThat(validator.validateUsername("1' UNION SELECT")).isInstanceOf(ValidationResult.Invalid::class.java)
    }

    // Password validation

    @Test
    fun `valid password accepted`() {
        val result = validator.validatePassword("StrongP1".toCharArray())
        assertThat(result).isInstanceOf(ValidationResult.Valid::class.java)
    }

    @Test
    fun `password too short rejected`() {
        val result = validator.validatePassword("Short1A".toCharArray())
        assertThat(result).isInstanceOf(ValidationResult.Invalid::class.java)
    }

    @Test
    fun `password without uppercase rejected`() {
        val result = validator.validatePassword("nouppercase1".toCharArray())
        assertThat(result).isInstanceOf(ValidationResult.Invalid::class.java)
    }

    @Test
    fun `password without lowercase rejected`() {
        val result = validator.validatePassword("NOLOWERCASE1".toCharArray())
        assertThat(result).isInstanceOf(ValidationResult.Invalid::class.java)
    }

    @Test
    fun `password without digit rejected`() {
        val result = validator.validatePassword("NoDigitHere".toCharArray())
        assertThat(result).isInstanceOf(ValidationResult.Invalid::class.java)
    }

    @Test
    fun `password at max length accepted`() {
        val password = ("Aa1" + "x".repeat(125)).toCharArray()
        val result = validator.validatePassword(password)
        assertThat(result).isInstanceOf(ValidationResult.Valid::class.java)
    }

    @Test
    fun `password exceeding max length rejected`() {
        val password = ("Aa1" + "x".repeat(126)).toCharArray()
        val result = validator.validatePassword(password)
        assertThat(result).isInstanceOf(ValidationResult.Invalid::class.java)
    }

    // Free text validation

    @Test
    fun `valid free text accepted`() {
        val result = validator.validateFreeText("Normal text content")
        assertThat(result).isInstanceOf(ValidationResult.Valid::class.java)
    }

    @Test
    fun `free text exceeding max length rejected`() {
        val longText = "a".repeat(501)
        val result = validator.validateFreeText(longText)
        assertThat(result).isInstanceOf(ValidationResult.Invalid::class.java)
    }

    // ID validation

    @Test
    fun `valid UUID accepted`() {
        val result = validator.validateId("550e8400-e29b-41d4-a716-446655440000")
        assertThat(result).isInstanceOf(ValidationResult.Valid::class.java)
    }

    @Test
    fun `invalid UUID rejected`() {
        assertThat(validator.validateId("not-a-uuid")).isInstanceOf(ValidationResult.Invalid::class.java)
        assertThat(validator.validateId("")).isInstanceOf(ValidationResult.Invalid::class.java)
        assertThat(validator.validateId("550e8400e29b41d4a716446655440000")).isInstanceOf(ValidationResult.Invalid::class.java)
    }

    // Sanitization

    @Test
    fun `sanitize removes null bytes`() {
        val result = validator.sanitize("hello\u0000world")
        assertThat(result).isEqualTo("hello world")
    }

    @Test
    fun `sanitize removes control characters`() {
        val result = validator.sanitize("hello\u0001\u0002world")
        assertThat(result).isEqualTo("helloworld")
    }

    @Test
    fun `sanitize collapses excessive whitespace`() {
        val result = validator.sanitize("hello    world")
        assertThat(result).isEqualTo("hello world")
    }

    @Test
    fun `sanitize trims leading and trailing whitespace`() {
        val result = validator.sanitize("  hello world  ")
        assertThat(result).isEqualTo("hello world")
    }
}
