package com.eaglepoint.storefront.domain.usecase

import com.eaglepoint.storefront.data.repository.AuditRepository
import com.eaglepoint.storefront.domain.model.AuditAction
import com.eaglepoint.storefront.domain.model.AuditEvent
import kotlinx.coroutines.flow.Flow

class GetAuditLogUseCase(
    private val auditRepository: AuditRepository,
    private val roleGuard: RoleGuard
) {

    fun getPage(limit: Int = 50, offset: Int = 0): Flow<List<AuditEvent>> {
        roleGuard.enforceAuditLogAccess()
        return auditRepository.getPage(limit, offset)
    }

    fun getByAction(action: AuditAction): Flow<List<AuditEvent>> {
        roleGuard.enforceAuditLogAccess()
        return auditRepository.getByAction(action)
    }

    fun getByDateRange(from: Long, to: Long): Flow<List<AuditEvent>> {
        roleGuard.enforceAuditLogAccess()
        return auditRepository.getByDateRange(from, to)
    }
}
