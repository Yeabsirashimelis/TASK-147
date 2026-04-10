package com.eaglepoint.storefront.domain.usecase

import com.eaglepoint.storefront.data.repository.AuthRepository
import com.eaglepoint.storefront.domain.model.AuditAction
import com.eaglepoint.storefront.domain.model.AuthResult
import com.eaglepoint.storefront.domain.validation.InputValidator
import com.eaglepoint.storefront.domain.validation.ValidationResult
import com.eaglepoint.storefront.security.PasswordHasher
import com.eaglepoint.storefront.security.SessionManager

class LoginUseCase(
    private val authRepository: AuthRepository,
    private val passwordHasher: PasswordHasher,
    private val inputValidator: InputValidator,
    private val logAuditEvent: LogAuditEventUseCase,
    private val sessionManager: SessionManager
) {

    suspend operator fun invoke(username: String, password: CharArray): AuthResult {
        // Validate inputs
        val usernameValidation = inputValidator.validateUsername(username)
        if (usernameValidation is ValidationResult.Invalid) {
            return AuthResult.Failure("Invalid username: ${usernameValidation.reasons.first()}")
        }

        val passwordValidation = inputValidator.validatePassword(password)
        if (passwordValidation is ValidationResult.Invalid) {
            return AuthResult.Failure("Invalid password: ${passwordValidation.reasons.first()}")
        }

        return try {
            val user = authRepository.findByUsername(username)
            if (user == null) {
                logAuditEvent(
                    userId = "unknown",
                    action = AuditAction.LOGIN_FAILURE,
                    target = "auth",
                    detail = "User not found: $username"
                )
                return AuthResult.Failure("Invalid credentials")
            }

            val isValid = passwordHasher.verify(password, user.passwordSalt, user.passwordHash)
            if (!isValid) {
                logAuditEvent(
                    userId = user.id,
                    action = AuditAction.LOGIN_FAILURE,
                    target = "auth",
                    detail = "Invalid password attempt"
                )
                return AuthResult.Failure("Invalid credentials")
            }

            sessionManager.startSession(user.id, user.role)

            logAuditEvent(
                userId = user.id,
                action = AuditAction.LOGIN_SUCCESS,
                target = "auth"
            )
            AuthResult.Success(user)
        } finally {
            password.fill('\u0000')
        }
    }
}
