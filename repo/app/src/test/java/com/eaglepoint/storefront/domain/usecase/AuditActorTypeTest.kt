package com.eaglepoint.storefront.domain.usecase

import com.eaglepoint.storefront.data.repository.AuditRepository
import com.eaglepoint.storefront.domain.model.ActorType
import com.eaglepoint.storefront.domain.model.AuditAction
import com.eaglepoint.storefront.domain.model.AuditEvent
import com.google.common.truth.Truth.assertThat
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class AuditActorTypeTest {

    private lateinit var auditRepository: AuditRepository
    private lateinit var logAuditEvent: LogAuditEventUseCase

    @BeforeEach
    fun setUp() {
        auditRepository = mockk(relaxed = true)
        logAuditEvent = LogAuditEventUseCase(auditRepository)
    }

    @Test
    fun `normal user audit event has USER actor type and preserves userId`() = runTest {
        val eventSlot = slot<AuditEvent>()
        coVerify(exactly = 0) { auditRepository.log(any()) }

        logAuditEvent(
            userId = "user-123",
            action = AuditAction.LOGIN_SUCCESS,
            target = "auth"
        )

        coVerify { auditRepository.log(capture(eventSlot)) }
        val event = eventSlot.captured
        assertThat(event.actorType).isEqualTo(ActorType.USER)
        assertThat(event.userId).isEqualTo("user-123")
    }

    @Test
    fun `system actor audit event has SYSTEM type and null userId`() = runTest {
        val eventSlot = slot<AuditEvent>()

        logAuditEvent(
            userId = "system",
            action = AuditAction.INGESTION_STARTED,
            target = "source_rule",
            targetId = "rule-1"
        )

        coVerify { auditRepository.log(capture(eventSlot)) }
        val event = eventSlot.captured
        assertThat(event.actorType).isEqualTo(ActorType.SYSTEM)
        assertThat(event.userId).isNull()
    }

    @Test
    fun `unknown actor audit event has UNKNOWN type and null userId`() = runTest {
        val eventSlot = slot<AuditEvent>()

        logAuditEvent(
            userId = "unknown",
            action = AuditAction.LOGIN_FAILURE,
            target = "auth",
            detail = "User not found"
        )

        coVerify { auditRepository.log(capture(eventSlot)) }
        val event = eventSlot.captured
        assertThat(event.actorType).isEqualTo(ActorType.UNKNOWN)
        assertThat(event.userId).isNull()
    }

    @Test
    fun `system and unknown audit events can be persisted without FK violation`() = runTest {
        // Both system and unknown result in null userId, which avoids FK constraint
        logAuditEvent(userId = "system", action = AuditAction.INGESTION_COMPLETED, target = "source_rule")
        logAuditEvent(userId = "unknown", action = AuditAction.LOGIN_FAILURE, target = "auth")

        coVerify(exactly = 2) { auditRepository.log(match { it.userId == null }) }
    }
}
