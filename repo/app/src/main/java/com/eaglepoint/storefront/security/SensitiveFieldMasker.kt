package com.eaglepoint.storefront.security

object SensitiveFieldMasker {

    private val SENSITIVE_KEYS = setOf(
        "passwordHash",
        "passwordSalt",
        "password",
        "secret",
        "token",
        "credential",
        "authorization",
        "apiKey",
        "ssn",
        "creditCard"
    )

    private val SENSITIVE_CONTENT_PATTERNS = listOf(
        Regex("password\\s*[:=]\\s*\\S+", RegexOption.IGNORE_CASE),
        Regex("token\\s*[:=]\\s*\\S+", RegexOption.IGNORE_CASE),
        Regex("secret\\s*[:=]\\s*\\S+", RegexOption.IGNORE_CASE)
    )

    fun maskHash(hash: ByteArray): String {
        if (hash.isEmpty()) return "****"
        val prefix = hash.take(2).joinToString("") { "%02x".format(it) }
        return "${prefix}..."
    }

    fun maskId(id: String): String {
        if (id.length <= 6) return "****"
        return "${id.take(4)}****${id.takeLast(2)}"
    }

    fun maskForLog(key: String, value: Any?): String {
        if (value == null) return "null"
        if (isSensitive(key)) return "****"
        val str = value.toString()
        return maskSensitiveContent(str)
    }

    fun isSensitive(key: String): Boolean {
        return SENSITIVE_KEYS.any { key.contains(it, ignoreCase = true) }
    }

    fun maskDetailMap(details: Map<String, Any?>): Map<String, String> {
        return details.mapValues { (key, value) -> maskForLog(key, value) }
    }

    /**
     * Masks any sensitive-looking content embedded in a free-text string,
     * such as audit detail fields that may contain password or token values.
     */
    fun maskSensitiveContent(text: String): String {
        var result = text
        for (pattern in SENSITIVE_CONTENT_PATTERNS) {
            result = pattern.replace(result) { match ->
                val parts = match.value.split(Regex("[:=]\\s*"), limit = 2)
                if (parts.size == 2) "${parts[0]}=****" else "****"
            }
        }
        return result
    }

    /**
     * Masks an audit detail string to ensure no sensitive data leaks.
     */
    fun maskAuditDetail(detail: String?): String? {
        if (detail == null) return null
        return maskSensitiveContent(detail)
    }
}
