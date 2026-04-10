package com.eaglepoint.storefront.domain.usecase

import com.eaglepoint.storefront.data.repository.AuthRepository
import com.eaglepoint.storefront.domain.model.AuditAction
import com.eaglepoint.storefront.domain.model.AuthResult
import com.eaglepoint.storefront.domain.model.User
import com.eaglepoint.storefront.domain.model.UserRole
import com.eaglepoint.storefront.domain.validation.InputValidator
import com.eaglepoint.storefront.security.PasswordHasher
import com.eaglepoint.storefront.security.SessionManager
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class LoginUseCaseTest {

    private lateinit var authRepository: AuthRepository
    private lateinit var passwordHasher: PasswordHasher
    private lateinit var inputValidator: InputValidator
    private lateinit var logAuditEvent: LogAuditEventUseCase
    private lateinit var sessionManager: SessionManager
    private lateinit var loginUseCase: LoginUseCase

    private val testSalt = ByteArray(32) { it.toByte() }
    private val testHash = ByteArray(32) { (it + 100).toByte() }
    private val testUser = User(
        id = "test-user-id",
        username = "testuser",
        passwordHash = testHash,
        passwordSalt = testSalt,
        createdAt = 1000L,
        updatedAt = 1000L,
        isActive = true
    )

    @BeforeEach
    fun setUp() {
        authRepository = mockk(relaxed = true)
        passwordHasher = mockk(relaxed = true)
        inputValidator = InputValidator()
        logAuditEvent = mockk(relaxed = true)
        sessionManager = mockk(relaxed = true)

        loginUseCase = LoginUseCase(authRepository, passwordHasher, inputValidator, logAuditEvent, sessionManager)
    }

    @Test
    fun `returns Success when credentials match`() = runTest {
        coEvery { authRepository.findByUsername("testuser") } returns testUser
        coEvery { passwordHasher.verify(any(), eq(testSalt), eq(testHash)) } returns true

        val result = loginUseCase("testuser", "ValidPass1".toCharArray())

        assertThat(result).isInstanceOf(AuthResult.Success::class.java)
        assertThat((result as AuthResult.Success).user.id).isEqualTo("test-user-id")
    }

    @Test
    fun `returns Failure when user not found`() = runTest {
        coEvery { authRepository.findByUsername("unknown") } returns null

        val result = loginUseCase("unknown", "ValidPass1".toCharArray())

        assertThat(result).isInstanceOf(AuthResult.Failure::class.java)
        assertThat((result as AuthResult.Failure).reason).isEqualTo("Invalid credentials")
    }

    @Test
    fun `returns Failure when password is wrong`() = runTest {
        coEvery { authRepository.findByUsername("testuser") } returns testUser
        coEvery { passwordHasher.verify(any(), any(), any()) } returns false

        val result = loginUseCase("testuser", "WrongPass1".toCharArray())

        assertThat(result).isInstanceOf(AuthResult.Failure::class.java)
    }

    @Test
    fun `logs LOGIN_SUCCESS on successful login`() = runTest {
        coEvery { authRepository.findByUsername("testuser") } returns testUser
        coEvery { passwordHasher.verify(any(), any(), any()) } returns true

        loginUseCase("testuser", "ValidPass1".toCharArray())

        coVerify {
            logAuditEvent(
                userId = "test-user-id",
                action = AuditAction.LOGIN_SUCCESS,
                target = "auth",
                targetId = null,
                detail = null
            )
        }
    }

    @Test
    fun `logs LOGIN_FAILURE when user not found`() = runTest {
        coEvery { authRepository.findByUsername("unknown") } returns null

        loginUseCase("unknown", "ValidPass1".toCharArray())

        coVerify {
            logAuditEvent(
                userId = "unknown",
                action = AuditAction.LOGIN_FAILURE,
                target = "auth",
                targetId = null,
                detail = "User not found: unknown"
            )
        }
    }

    @Test
    fun `logs LOGIN_FAILURE when password is wrong`() = runTest {
        coEvery { authRepository.findByUsername("testuser") } returns testUser
        coEvery { passwordHasher.verify(any(), any(), any()) } returns false

        loginUseCase("testuser", "WrongPass1".toCharArray())

        coVerify {
            logAuditEvent(
                userId = "test-user-id",
                action = AuditAction.LOGIN_FAILURE,
                target = "auth",
                targetId = null,
                detail = "Invalid password attempt"
            )
        }
    }

    @Test
    fun `rejects invalid username without hitting repository`() = runTest {
        val result = loginUseCase("ab", "ValidPass1".toCharArray())

        assertThat(result).isInstanceOf(AuthResult.Failure::class.java)
        coVerify(exactly = 0) { authRepository.findByUsername(any()) }
    }

    @Test
    fun `rejects invalid password format without hitting repository`() = runTest {
        val result = loginUseCase("testuser", "short".toCharArray())

        assertThat(result).isInstanceOf(AuthResult.Failure::class.java)
        coVerify(exactly = 0) { authRepository.findByUsername(any()) }
    }
}
