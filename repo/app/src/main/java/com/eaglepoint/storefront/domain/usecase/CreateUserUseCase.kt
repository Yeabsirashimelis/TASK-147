package com.eaglepoint.storefront.domain.usecase

import com.eaglepoint.storefront.data.repository.AuthRepository
import com.eaglepoint.storefront.domain.model.AuditAction
import com.eaglepoint.storefront.domain.model.AuthResult
import com.eaglepoint.storefront.domain.model.User
import com.eaglepoint.storefront.domain.model.UserRole
import com.eaglepoint.storefront.domain.validation.InputValidator
import com.eaglepoint.storefront.domain.validation.ValidationResult
import com.eaglepoint.storefront.security.PasswordHasher
import com.eaglepoint.storefront.security.SessionManager
import java.util.UUID

class CreateUserUseCase(
    private val authRepository: AuthRepository,
    private val passwordHasher: PasswordHasher,
    private val inputValidator: InputValidator,
    private val logAuditEvent: LogAuditEventUseCase,
    private val sessionManager: SessionManager
) {

    suspend operator fun invoke(username: String, password: CharArray, role: UserRole = UserRole.ADMIN): AuthResult {
        // Validate inputs
        val usernameValidation = inputValidator.validateUsername(username)
        if (usernameValidation is ValidationResult.Invalid) {
            return AuthResult.Failure("Invalid username: ${usernameValidation.reasons.joinToString()}")
        }

        val passwordValidation = inputValidator.validatePassword(password)
        if (passwordValidation is ValidationResult.Invalid) {
            return AuthResult.Failure("Invalid password: ${passwordValidation.reasons.joinToString()}")
        }

        return try {
            // Check if username already exists
            val existing = authRepository.findByUsername(username)
            if (existing != null) {
                return AuthResult.Failure("Username already exists")
            }

            val salt = passwordHasher.generateSalt()
            val hash = passwordHasher.hash(password, salt)
            val userId = UUID.randomUUID().toString()
            val now = System.currentTimeMillis()

            authRepository.createUser(
                id = userId,
                username = username,
                passwordHash = hash,
                passwordSalt = salt,
                timestamp = now,
                role = role.name
            )

            logAuditEvent(
                userId = userId,
                action = AuditAction.USER_CREATED,
                target = "user",
                targetId = userId,
                detail = "User created: $username"
            )

            val user = User(
                id = userId,
                username = username,
                passwordHash = hash,
                passwordSalt = salt,
                role = role,
                createdAt = now,
                updatedAt = now,
                isActive = true
            )
            sessionManager.startSession(userId, role)
            AuthResult.Success(user)
        } finally {
            password.fill('\u0000')
        }
    }
}
