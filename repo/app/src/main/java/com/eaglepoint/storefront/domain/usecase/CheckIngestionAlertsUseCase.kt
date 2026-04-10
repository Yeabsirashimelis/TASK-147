package com.eaglepoint.storefront.domain.usecase

import com.eaglepoint.storefront.data.repository.IngestionAlertRepository
import com.eaglepoint.storefront.domain.model.AuditAction
import com.eaglepoint.storefront.domain.model.IngestionAlert
import com.eaglepoint.storefront.security.SessionManager
import kotlinx.coroutines.flow.Flow

class CheckIngestionAlertsUseCase(
    private val alertRepository: IngestionAlertRepository,
    private val logAuditEvent: LogAuditEventUseCase,
    private val sessionManager: SessionManager
) {

    fun getUnacknowledged(): Flow<List<IngestionAlert>> {
        return alertRepository.getUnacknowledged()
    }

    fun getAll(limit: Int = 50, offset: Int = 0): Flow<List<IngestionAlert>> {
        return alertRepository.getAll(limit, offset)
    }

    fun getUnacknowledgedCount(): Flow<Int> {
        return alertRepository.getUnacknowledgedCount()
    }

    suspend fun acknowledge(alertId: String) {
        val userId = sessionManager.requireUserId()
        alertRepository.acknowledge(alertId)
        logAuditEvent(
            userId = userId,
            action = AuditAction.INGESTION_ALERT_ACKNOWLEDGED,
            target = "ingestion_alert",
            targetId = alertId
        )
    }
}
