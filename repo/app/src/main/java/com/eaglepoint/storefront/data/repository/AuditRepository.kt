package com.eaglepoint.storefront.data.repository

import com.eaglepoint.storefront.data.db.dao.AuditEventDao
import com.eaglepoint.storefront.data.db.entity.AuditEventEntity
import com.eaglepoint.storefront.domain.model.ActorType
import com.eaglepoint.storefront.domain.model.AuditAction
import com.eaglepoint.storefront.domain.model.AuditEvent
import com.eaglepoint.storefront.security.SensitiveFieldMasker
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class AuditRepository(
    private val auditEventDao: AuditEventDao,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    suspend fun log(event: AuditEvent) = withContext(dispatcher) {
        val maskedDetail = event.detail?.let { detail ->
            if (SensitiveFieldMasker.isSensitive("detail")) "****" else detail
        }
        val entity = AuditEventEntity(
            id = event.id,
            userId = event.userId,
            actorType = event.actorType.name,
            action = event.action.name,
            target = event.target,
            targetId = event.targetId?.let { SensitiveFieldMasker.maskId(it) },
            timestamp = event.timestamp,
            detail = maskedDetail
        )
        auditEventDao.insert(entity)
    }

    fun getPage(limit: Int, offset: Int): Flow<List<AuditEvent>> {
        return auditEventDao.getPage(limit, offset).map { entities ->
            entities.map { entityToDomain(it) }
        }
    }

    fun getByAction(action: AuditAction): Flow<List<AuditEvent>> {
        return auditEventDao.getByAction(action.name).map { entities ->
            entities.map { entityToDomain(it) }
        }
    }

    fun getByDateRange(from: Long, to: Long): Flow<List<AuditEvent>> {
        return auditEventDao.getByDateRange(from, to).map { entities ->
            entities.map { entityToDomain(it) }
        }
    }

    suspend fun count(): Int = withContext(dispatcher) {
        auditEventDao.count()
    }

    fun getAll(): Flow<List<AuditEvent>> {
        return auditEventDao.getPage(Int.MAX_VALUE, 0).map { entities ->
            entities.map { entityToDomain(it) }
        }
    }

    private fun entityToDomain(entity: AuditEventEntity): AuditEvent {
        return AuditEvent(
            id = entity.id,
            userId = entity.userId,
            actorType = try { ActorType.valueOf(entity.actorType) } catch (_: Exception) { ActorType.USER },
            action = AuditAction.valueOf(entity.action),
            target = entity.target,
            targetId = entity.targetId,
            timestamp = entity.timestamp,
            detail = entity.detail
        )
    }
}
