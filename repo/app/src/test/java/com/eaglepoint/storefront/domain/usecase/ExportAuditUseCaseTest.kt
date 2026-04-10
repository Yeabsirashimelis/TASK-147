package com.eaglepoint.storefront.domain.usecase

import com.eaglepoint.storefront.data.repository.AuditRepository
import com.eaglepoint.storefront.domain.model.ActorType
import com.eaglepoint.storefront.domain.model.AuditAction
import com.eaglepoint.storefront.domain.model.AuditEvent
import com.eaglepoint.storefront.domain.model.UserRole
import com.eaglepoint.storefront.security.SessionManager
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.File

class ExportAuditUseCaseTest {

    private lateinit var auditRepository: AuditRepository
    private lateinit var sessionManager: SessionManager
    private lateinit var roleGuard: RoleGuard
    private lateinit var reAuthenticateUseCase: ReAuthenticateUseCase
    private lateinit var logAuditEvent: LogAuditEventUseCase
    private lateinit var exportAuditUseCase: ExportAuditUseCase
    private lateinit var tempFile: File

    private val testDispatcher = StandardTestDispatcher()

    @BeforeEach
    fun setUp() {
        auditRepository = mockk(relaxed = true)
        sessionManager = mockk(relaxed = true)
        roleGuard = RoleGuard(sessionManager)
        reAuthenticateUseCase = mockk(relaxed = true)
        logAuditEvent = mockk(relaxed = true)
        exportAuditUseCase = ExportAuditUseCase(
            auditRepository, roleGuard, reAuthenticateUseCase,
            logAuditEvent, sessionManager, testDispatcher
        )
        tempFile = File.createTempFile("audit_export_test", ".csv")
        tempFile.deleteOnExit()
    }

    @Test
    fun `non-admin cannot export audit logs`() = runTest(testDispatcher) {
        every { sessionManager.requireRole() } returns UserRole.EDITOR
        every { sessionManager.requireUserId() } returns "editor-1"

        assertThrows<SecurityException> {
            exportAuditUseCase.export(tempFile, "pass".toCharArray())
        }
    }

    @Test
    fun `export fails if re-authentication fails`() = runTest(testDispatcher) {
        every { sessionManager.requireRole() } returns UserRole.ADMIN
        every { sessionManager.requireUserId() } returns "admin-1"
        coEvery { reAuthenticateUseCase("admin-1", any()) } returns false

        val result = exportAuditUseCase.export(tempFile, "wrongpass".toCharArray())

        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()?.message).contains("Re-authentication failed")
    }

    @Test
    fun `admin with valid re-auth can export`() = runTest(testDispatcher) {
        every { sessionManager.requireRole() } returns UserRole.ADMIN
        every { sessionManager.requireUserId() } returns "admin-1"
        coEvery { reAuthenticateUseCase("admin-1", any()) } returns true

        val events = listOf(
            AuditEvent(
                id = "e1", userId = "user-1", actorType = ActorType.USER,
                action = AuditAction.LOGIN_SUCCESS, timestamp = System.currentTimeMillis()
            )
        )
        coEvery { auditRepository.getAll() } returns flowOf(events)

        val result = exportAuditUseCase.export(tempFile, "ValidPass1".toCharArray())

        assertThat(result.isSuccess).isTrue()
        assertThat(tempFile.readText()).contains("LOGIN_SUCCESS")
    }

    @Test
    fun `export logs EXPORT_INITIATED and EXPORT_COMPLETED`() = runTest(testDispatcher) {
        every { sessionManager.requireRole() } returns UserRole.ADMIN
        every { sessionManager.requireUserId() } returns "admin-1"
        coEvery { reAuthenticateUseCase("admin-1", any()) } returns true
        coEvery { auditRepository.getAll() } returns flowOf(emptyList())

        exportAuditUseCase.export(tempFile, "ValidPass1".toCharArray())

        coVerify { logAuditEvent("admin-1", AuditAction.EXPORT_INITIATED, "audit_events", null, any()) }
        coVerify { logAuditEvent("admin-1", AuditAction.EXPORT_COMPLETED, "audit_events", null, any()) }
    }

    @Test
    fun `export writes CSV with header and data rows`() = runTest(testDispatcher) {
        every { sessionManager.requireRole() } returns UserRole.ADMIN
        every { sessionManager.requireUserId() } returns "admin-1"
        coEvery { reAuthenticateUseCase("admin-1", any()) } returns true

        val events = listOf(
            AuditEvent(
                id = "e1", userId = "user-1", actorType = ActorType.USER,
                action = AuditAction.LOGIN_SUCCESS, timestamp = 1700000000000L
            ),
            AuditEvent(
                id = "e2", userId = null, actorType = ActorType.SYSTEM,
                action = AuditAction.INGESTION_STARTED, timestamp = 1700000001000L,
                target = "source_rule", targetId = "rule-1"
            )
        )
        coEvery { auditRepository.getAll() } returns flowOf(events)

        exportAuditUseCase.export(tempFile, "ValidPass1".toCharArray())

        val lines = tempFile.readLines()
        assertThat(lines[0]).contains("id,timestamp,actor_type,user_id,action")
        assertThat(lines).hasSize(3) // header + 2 data rows
        assertThat(lines[1]).contains("USER")
        assertThat(lines[2]).contains("SYSTEM")
    }
}
