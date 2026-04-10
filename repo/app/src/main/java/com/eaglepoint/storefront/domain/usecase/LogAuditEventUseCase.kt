package com.eaglepoint.storefront.domain.usecase

import com.eaglepoint.storefront.data.repository.AuditRepository
import com.eaglepoint.storefront.domain.model.ActorType
import com.eaglepoint.storefront.domain.model.AuditAction
import com.eaglepoint.storefront.domain.model.AuditEvent
import com.eaglepoint.storefront.security.SensitiveFieldMasker
import java.util.UUID

class LogAuditEventUseCase(
    private val auditRepository: AuditRepository
) {

    suspend operator fun invoke(
        userId: String,
        action: AuditAction,
        target: String? = null,
        targetId: String? = null,
        detail: String? = null
    ) {
        val actorType = when (userId) {
            SYSTEM_ACTOR -> ActorType.SYSTEM
            UNKNOWN_ACTOR -> ActorType.UNKNOWN
            else -> ActorType.USER
        }
        val resolvedUserId = when (actorType) {
            ActorType.USER -> userId
            else -> null
        }

        val event = AuditEvent(
            id = UUID.randomUUID().toString(),
            userId = resolvedUserId,
            actorType = actorType,
            action = action,
            target = target,
            targetId = targetId,
            timestamp = System.currentTimeMillis(),
            detail = SensitiveFieldMasker.maskAuditDetail(detail)
        )
        auditRepository.log(event)
    }

    companion object {
        const val SYSTEM_ACTOR = "system"
        const val UNKNOWN_ACTOR = "unknown"
    }
}
