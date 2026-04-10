package com.eaglepoint.storefront.domain.usecase

import com.eaglepoint.storefront.data.repository.AuthRepository
import com.eaglepoint.storefront.domain.model.AuditAction
import com.eaglepoint.storefront.security.PasswordHasher

class ReAuthenticateUseCase(
    private val authRepository: AuthRepository,
    private val passwordHasher: PasswordHasher,
    private val logAuditEvent: LogAuditEventUseCase
) {

    suspend operator fun invoke(userId: String, password: CharArray): Boolean {
        return try {
            val user = authRepository.findById(userId)
                ?: return false.also {
                    logAuditEvent(
                        userId = userId,
                        action = AuditAction.REAUTH_FAILURE,
                        target = "auth",
                        detail = "User not found for re-authentication"
                    )
                }

            val isValid = passwordHasher.verify(password, user.passwordSalt, user.passwordHash)

            if (isValid) {
                logAuditEvent(
                    userId = userId,
                    action = AuditAction.REAUTH_SUCCESS,
                    target = "auth"
                )
            } else {
                logAuditEvent(
                    userId = userId,
                    action = AuditAction.REAUTH_FAILURE,
                    target = "auth",
                    detail = "Invalid password during re-authentication"
                )
            }

            isValid
        } finally {
            password.fill('\u0000')
        }
    }
}
