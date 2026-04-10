package com.eaglepoint.storefront.domain.usecase

import com.eaglepoint.storefront.data.repository.IngestionAlertRepository
import com.eaglepoint.storefront.domain.model.AuditAction
import com.eaglepoint.storefront.security.SessionManager
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class CheckIngestionAlertsUseCaseTest {

    private lateinit var alertRepository: IngestionAlertRepository
    private lateinit var logAuditEvent: LogAuditEventUseCase
    private lateinit var sessionManager: SessionManager
    private lateinit var checkAlertsUseCase: CheckIngestionAlertsUseCase

    @BeforeEach
    fun setUp() {
        alertRepository = mockk(relaxed = true)
        logAuditEvent = mockk(relaxed = true)
        sessionManager = mockk(relaxed = true)
        every { sessionManager.requireUserId() } returns "admin"
        checkAlertsUseCase = CheckIngestionAlertsUseCase(alertRepository, logAuditEvent, sessionManager)
    }

    @Test
    fun `acknowledge updates repository and logs audit event with session userId`() = runTest {
        checkAlertsUseCase.acknowledge("alert-1")

        coVerify { alertRepository.acknowledge("alert-1") }
        coVerify {
            logAuditEvent(
                userId = "admin",
                action = AuditAction.INGESTION_ALERT_ACKNOWLEDGED,
                target = "ingestion_alert",
                targetId = "alert-1",
                detail = null
            )
        }
    }
}
