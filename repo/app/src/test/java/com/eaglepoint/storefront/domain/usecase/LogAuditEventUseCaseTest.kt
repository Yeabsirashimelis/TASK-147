package com.eaglepoint.storefront.domain.usecase

import com.eaglepoint.storefront.data.repository.AuditRepository
import com.eaglepoint.storefront.domain.model.AuditAction
import com.eaglepoint.storefront.domain.model.AuditEvent
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class LogAuditEventUseCaseTest {

    private lateinit var auditRepository: AuditRepository
    private lateinit var logAuditEventUseCase: LogAuditEventUseCase

    @BeforeEach
    fun setUp() {
        auditRepository = mockk(relaxed = true)
        logAuditEventUseCase = LogAuditEventUseCase(auditRepository)
    }

    @Test
    fun `logs event with correct fields`() = runTest {
        val eventSlot = slot<AuditEvent>()
        coEvery { auditRepository.log(capture(eventSlot)) } returns Unit

        logAuditEventUseCase(
            userId = "user-123",
            action = AuditAction.LOGIN_SUCCESS,
            target = "auth",
            targetId = "target-456",
            detail = "Logged in successfully"
        )

        coVerify(exactly = 1) { auditRepository.log(any()) }

        val captured = eventSlot.captured
        assertThat(captured.userId).isEqualTo("user-123")
        assertThat(captured.action).isEqualTo(AuditAction.LOGIN_SUCCESS)
        assertThat(captured.target).isEqualTo("auth")
        assertThat(captured.targetId).isEqualTo("target-456")
        assertThat(captured.detail).isEqualTo("Logged in successfully")
        assertThat(captured.id).isNotEmpty()
        assertThat(captured.timestamp).isGreaterThan(0L)
    }

    @Test
    fun `generates unique IDs for each event`() = runTest {
        val events = mutableListOf<AuditEvent>()
        coEvery { auditRepository.log(capture(events)) } returns Unit

        logAuditEventUseCase(userId = "user-1", action = AuditAction.LOGIN_SUCCESS)
        logAuditEventUseCase(userId = "user-1", action = AuditAction.LOGIN_SUCCESS)

        assertThat(events[0].id).isNotEqualTo(events[1].id)
    }

    @Test
    fun `handles null optional fields`() = runTest {
        val eventSlot = slot<AuditEvent>()
        coEvery { auditRepository.log(capture(eventSlot)) } returns Unit

        logAuditEventUseCase(
            userId = "user-123",
            action = AuditAction.EXPORT_INITIATED
        )

        val captured = eventSlot.captured
        assertThat(captured.target).isNull()
        assertThat(captured.targetId).isNull()
        assertThat(captured.detail).isNull()
    }
}
