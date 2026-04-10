package com.eaglepoint.storefront.api

import com.eaglepoint.storefront.data.repository.AuthRepository
import com.eaglepoint.storefront.domain.model.AuditAction
import com.eaglepoint.storefront.domain.model.AuthResult
import com.eaglepoint.storefront.domain.model.User
import com.eaglepoint.storefront.domain.model.UserRole
import com.eaglepoint.storefront.domain.usecase.CreateUserUseCase
import com.eaglepoint.storefront.domain.usecase.LogAuditEventUseCase
import com.eaglepoint.storefront.domain.usecase.LoginUseCase
import com.eaglepoint.storefront.domain.validation.InputValidator
import com.eaglepoint.storefront.security.PasswordHasher
import com.eaglepoint.storefront.security.SessionManager
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * API-level functional tests for the authentication flow.
 * Tests the complete login/create-user interface under normal,
 * abnormal, boundary, and permission scenarios.
 */
class AuthenticationFlowTest {

    private lateinit var authRepository: AuthRepository
    private lateinit var passwordHasher: PasswordHasher
    private lateinit var inputValidator: InputValidator
    private lateinit var logAuditEvent: LogAuditEventUseCase
    private lateinit var sessionManager: SessionManager
    private lateinit var loginUseCase: LoginUseCase
    private lateinit var createUserUseCase: CreateUserUseCase

    private val testSalt = ByteArray(32) { it.toByte() }
    private val testHash = ByteArray(32) { (it + 100).toByte() }
    private val now = System.currentTimeMillis()

    private val testUser = User(
        id = "user-1", username = "testuser",
        passwordHash = testHash, passwordSalt = testSalt,
        role = UserRole.USER, createdAt = now, updatedAt = now
    )

    @BeforeEach
    fun setUp() {
        authRepository = mockk(relaxed = true)
        passwordHasher = mockk(relaxed = true)
        inputValidator = InputValidator()
        logAuditEvent = mockk(relaxed = true)
        sessionManager = mockk(relaxed = true)
        loginUseCase = LoginUseCase(authRepository, passwordHasher, inputValidator, logAuditEvent, sessionManager)
        createUserUseCase = CreateUserUseCase(authRepository, passwordHasher, inputValidator, logAuditEvent, sessionManager)
    }

    // --- Normal flow ---
    @Test
    fun `login with valid credentials returns Success and starts session`() = runTest {
        coEvery { authRepository.findByUsername("testuser") } returns testUser
        coEvery { passwordHasher.verify(any(), any(), any()) } returns true

        val result = loginUseCase("testuser", "ValidPass1".toCharArray())

        assertThat(result).isInstanceOf(AuthResult.Success::class.java)
        coVerify { sessionManager.startSession("user-1", UserRole.USER) }
        coVerify { logAuditEvent(userId = "user-1", action = AuditAction.LOGIN_SUCCESS, target = "auth", targetId = null, detail = null) }
    }

    @Test
    fun `create user with valid data returns Success with user object`() = runTest {
        coEvery { authRepository.findByUsername(any()) } returns null
        coEvery { passwordHasher.generateSalt() } returns testSalt
        coEvery { passwordHasher.hash(any(), any()) } returns testHash

        val result = createUserUseCase("newuser1", "StrongP1".toCharArray(), UserRole.ADMIN)

        assertThat(result).isInstanceOf(AuthResult.Success::class.java)
        val user = (result as AuthResult.Success).user
        assertThat(user.username).isEqualTo("newuser1")
        assertThat(user.role).isEqualTo(UserRole.ADMIN)
    }

    // --- Missing / invalid parameters ---
    @Test
    fun `login with empty username returns Failure`() = runTest {
        val result = loginUseCase("", "ValidPass1".toCharArray())
        assertThat(result).isInstanceOf(AuthResult.Failure::class.java)
    }

    @Test
    fun `login with too-short username returns Failure without hitting DB`() = runTest {
        val result = loginUseCase("ab", "ValidPass1".toCharArray())
        assertThat(result).isInstanceOf(AuthResult.Failure::class.java)
        coVerify(exactly = 0) { authRepository.findByUsername(any()) }
    }

    @Test
    fun `login with SQL injection username returns Failure`() = runTest {
        val result = loginUseCase("' OR 1=1 --", "ValidPass1".toCharArray())
        assertThat(result).isInstanceOf(AuthResult.Failure::class.java)
    }

    @Test
    fun `login with password missing uppercase returns Failure`() = runTest {
        val result = loginUseCase("testuser", "nouppercase1".toCharArray())
        assertThat(result).isInstanceOf(AuthResult.Failure::class.java)
    }

    @Test
    fun `login with password missing digit returns Failure`() = runTest {
        val result = loginUseCase("testuser", "NoDigitHere".toCharArray())
        assertThat(result).isInstanceOf(AuthResult.Failure::class.java)
    }

    @Test
    fun `login with too-short password returns Failure`() = runTest {
        val result = loginUseCase("testuser", "Sh1".toCharArray())
        assertThat(result).isInstanceOf(AuthResult.Failure::class.java)
    }

    // --- Wrong credentials ---
    @Test
    fun `login with non-existent user returns generic Failure message`() = runTest {
        coEvery { authRepository.findByUsername("unknown") } returns null

        val result = loginUseCase("unknown", "ValidPass1".toCharArray())

        assertThat(result).isInstanceOf(AuthResult.Failure::class.java)
        assertThat((result as AuthResult.Failure).reason).isEqualTo("Invalid credentials")
        coVerify { logAuditEvent(userId = "unknown", action = AuditAction.LOGIN_FAILURE, target = "auth", targetId = null, detail = any()) }
    }

    @Test
    fun `login with wrong password returns generic Failure message`() = runTest {
        coEvery { authRepository.findByUsername("testuser") } returns testUser
        coEvery { passwordHasher.verify(any(), any(), any()) } returns false

        val result = loginUseCase("testuser", "WrongPass1".toCharArray())

        assertThat(result).isInstanceOf(AuthResult.Failure::class.java)
        assertThat((result as AuthResult.Failure).reason).isEqualTo("Invalid credentials")
    }

    // --- Duplicate user creation ---
    @Test
    fun `create user with existing username returns Failure`() = runTest {
        coEvery { authRepository.findByUsername("testuser") } returns testUser

        val result = createUserUseCase("testuser", "StrongP1".toCharArray())

        assertThat(result).isInstanceOf(AuthResult.Failure::class.java)
        assertThat((result as AuthResult.Failure).reason).contains("already exists")
    }

    // --- Password zeroed after use ---
    @Test
    fun `password array is zeroed after login attempt`() = runTest {
        coEvery { authRepository.findByUsername("testuser") } returns testUser
        coEvery { passwordHasher.verify(any(), any(), any()) } returns true

        val password = "ValidPass1".toCharArray()
        loginUseCase("testuser", password)

        assertThat(password.all { it == '\u0000' }).isTrue()
    }

    // --- Audit logging for all outcomes ---
    @Test
    fun `failed login logs LOGIN_FAILURE audit event`() = runTest {
        coEvery { authRepository.findByUsername("testuser") } returns testUser
        coEvery { passwordHasher.verify(any(), any(), any()) } returns false

        loginUseCase("testuser", "WrongPass1".toCharArray())

        coVerify { logAuditEvent(userId = "user-1", action = AuditAction.LOGIN_FAILURE, target = "auth", targetId = null, detail = any()) }
    }

    @Test
    fun `user creation logs USER_CREATED audit event`() = runTest {
        coEvery { authRepository.findByUsername(any()) } returns null
        coEvery { passwordHasher.generateSalt() } returns testSalt
        coEvery { passwordHasher.hash(any(), any()) } returns testHash

        createUserUseCase("newuser2", "StrongP1".toCharArray())

        coVerify { logAuditEvent(userId = any(), action = AuditAction.USER_CREATED, target = "user", targetId = any(), detail = any()) }
    }
}
