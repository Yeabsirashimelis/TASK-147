package com.eaglepoint.storefront.domain.validation

sealed class ValidationResult {
    data object Valid : ValidationResult()
    data class Invalid(val reasons: List<String>) : ValidationResult()
}
