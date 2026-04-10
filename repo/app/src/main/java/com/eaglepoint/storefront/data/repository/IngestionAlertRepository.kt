package com.eaglepoint.storefront.data.repository

import com.eaglepoint.storefront.data.db.dao.IngestionAlertDao
import com.eaglepoint.storefront.data.db.entity.IngestionAlertEntity
import com.eaglepoint.storefront.domain.model.IngestionAlert
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class IngestionAlertRepository(
    private val alertDao: IngestionAlertDao,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    suspend fun create(alert: IngestionAlert) = withContext(dispatcher) {
        alertDao.insert(
            IngestionAlertEntity(
                id = alert.id,
                sourceRuleId = alert.sourceRuleId,
                sourceName = alert.sourceName,
                failureCount = alert.failureCount,
                message = alert.message,
                isAcknowledged = alert.isAcknowledged,
                createdAt = alert.createdAt
            )
        )
    }

    fun getUnacknowledged(): Flow<List<IngestionAlert>> {
        return alertDao.getUnacknowledged().map { entities ->
            entities.map { entityToDomain(it) }
        }
    }

    fun getAll(limit: Int = 50, offset: Int = 0): Flow<List<IngestionAlert>> {
        return alertDao.getAll(limit, offset).map { entities ->
            entities.map { entityToDomain(it) }
        }
    }

    suspend fun acknowledge(id: String) = withContext(dispatcher) {
        alertDao.acknowledge(id)
    }

    fun getUnacknowledgedCount(): Flow<Int> {
        return alertDao.getUnacknowledgedCount()
    }

    suspend fun hasUnacknowledgedForSource(sourceRuleId: String): Boolean = withContext(dispatcher) {
        alertDao.hasUnacknowledgedForSource(sourceRuleId)
    }

    private fun entityToDomain(entity: IngestionAlertEntity): IngestionAlert {
        return IngestionAlert(
            id = entity.id,
            sourceRuleId = entity.sourceRuleId,
            sourceName = entity.sourceName,
            failureCount = entity.failureCount,
            message = entity.message,
            isAcknowledged = entity.isAcknowledged,
            createdAt = entity.createdAt
        )
    }
}
