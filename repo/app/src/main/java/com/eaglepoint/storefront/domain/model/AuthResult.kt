package com.eaglepoint.storefront.domain.model

sealed class AuthResult {
    data class Success(val user: User) : AuthResult()
    data class Failure(val reason: String) : AuthResult()
    data object AccountLocked : AuthResult()
}
