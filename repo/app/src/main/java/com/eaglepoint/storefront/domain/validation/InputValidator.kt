package com.eaglepoint.storefront.domain.validation

class InputValidator {

    fun validateUsername(input: String): ValidationResult {
        val reasons = mutableListOf<String>()
        val sanitized = sanitize(input)

        if (sanitized.length < USERNAME_MIN_LENGTH) {
            reasons.add("Username must be at least $USERNAME_MIN_LENGTH characters")
        }
        if (sanitized.length > USERNAME_MAX_LENGTH) {
            reasons.add("Username must be at most $USERNAME_MAX_LENGTH characters")
        }
        if (!USERNAME_PATTERN.matches(sanitized)) {
            reasons.add("Username may only contain letters, digits, and underscores")
        }

        return if (reasons.isEmpty()) ValidationResult.Valid else ValidationResult.Invalid(reasons)
    }

    fun validatePassword(input: CharArray): ValidationResult {
        val reasons = mutableListOf<String>()

        if (input.size < PASSWORD_MIN_LENGTH) {
            reasons.add("Password must be at least $PASSWORD_MIN_LENGTH characters")
        }
        if (input.size > PASSWORD_MAX_LENGTH) {
            reasons.add("Password must be at most $PASSWORD_MAX_LENGTH characters")
        }
        if (input.none { it.isUpperCase() }) {
            reasons.add("Password must contain at least one uppercase letter")
        }
        if (input.none { it.isLowerCase() }) {
            reasons.add("Password must contain at least one lowercase letter")
        }
        if (input.none { it.isDigit() }) {
            reasons.add("Password must contain at least one digit")
        }

        return if (reasons.isEmpty()) ValidationResult.Valid else ValidationResult.Invalid(reasons)
    }

    fun validateFreeText(input: String, maxLength: Int = FREE_TEXT_MAX_LENGTH): ValidationResult {
        val sanitized = sanitize(input)
        val reasons = mutableListOf<String>()

        if (sanitized.length > maxLength) {
            reasons.add("Text must be at most $maxLength characters")
        }

        return if (reasons.isEmpty()) ValidationResult.Valid else ValidationResult.Invalid(reasons)
    }

    fun validateId(input: String): ValidationResult {
        return if (UUID_PATTERN.matches(input)) {
            ValidationResult.Valid
        } else {
            ValidationResult.Invalid(listOf("Invalid ID format"))
        }
    }

    fun sanitize(input: String): String {
        return input
            .replace("\u0000", "")
            .replace(CONTROL_CHARS_REGEX, "")
            .replace(EXCESSIVE_WHITESPACE_REGEX, " ")
            .trim()
    }

    companion object {
        private const val USERNAME_MIN_LENGTH = 3
        private const val USERNAME_MAX_LENGTH = 64
        private const val PASSWORD_MIN_LENGTH = 8
        private const val PASSWORD_MAX_LENGTH = 128
        private const val FREE_TEXT_MAX_LENGTH = 500

        private val USERNAME_PATTERN = Regex("^[a-zA-Z0-9_]{3,64}$")
        private val UUID_PATTERN = Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")
        private val CONTROL_CHARS_REGEX = Regex("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F\\x7F]")
        private val EXCESSIVE_WHITESPACE_REGEX = Regex("\\s{2,}")
    }
}
