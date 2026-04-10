package com.eaglepoint.storefront.data.repository

import com.eaglepoint.storefront.data.db.dao.DataBatchVersionDao
import com.eaglepoint.storefront.data.db.dao.DataLineageDao
import com.eaglepoint.storefront.data.db.dao.IngestionJobRunDao
import com.eaglepoint.storefront.data.db.entity.DataBatchVersionEntity
import com.eaglepoint.storefront.data.db.entity.DataLineageEntity
import com.eaglepoint.storefront.data.db.entity.IngestionJobRunEntity
import com.eaglepoint.storefront.domain.model.BatchValidationStatus
import com.eaglepoint.storefront.domain.model.DataBatchVersion
import com.eaglepoint.storefront.domain.model.IngestionJobRun
import com.eaglepoint.storefront.domain.model.IngestionStatus
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.UUID

class IngestionJobRunRepository(
    private val jobRunDao: IngestionJobRunDao,
    private val batchVersionDao: DataBatchVersionDao,
    private val dataLineageDao: DataLineageDao,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    suspend fun createRun(run: IngestionJobRun) = withContext(dispatcher) {
        jobRunDao.insert(domainToEntity(run))
    }

    suspend fun updateRun(run: IngestionJobRun) = withContext(dispatcher) {
        jobRunDao.update(domainToEntity(run))
    }

    fun getBySourceRule(sourceRuleId: String, limit: Int = 50, offset: Int = 0): Flow<List<IngestionJobRun>> {
        return jobRunDao.getBySourceRule(sourceRuleId, limit, offset).map { entities ->
            entities.map { entityToDomain(it) }
        }
    }

    fun getAll(limit: Int = 50, offset: Int = 0): Flow<List<IngestionJobRun>> {
        return jobRunDao.getAll(limit, offset).map { entities ->
            entities.map { entityToDomain(it) }
        }
    }

    suspend fun countFailuresSince(sourceRuleId: String, since: Long): Int = withContext(dispatcher) {
        jobRunDao.countFailuresSince(sourceRuleId, since)
    }

    suspend fun getLatestForSource(sourceRuleId: String): IngestionJobRun? = withContext(dispatcher) {
        jobRunDao.getLatestForSource(sourceRuleId)?.let { entityToDomain(it) }
    }

    suspend fun createBatchVersion(
        sourceRuleId: String,
        ruleVersion: Int,
        itemsCount: Int,
        ingestionJobRunId: String
    ): DataBatchVersion = withContext(dispatcher) {
        val batchName = "ingestion_$sourceRuleId"
        val currentMax = batchVersionDao.getMaxVersion(sourceRuleId, batchName) ?: 0
        val newVersion = currentMax + 1
        val now = System.currentTimeMillis()
        val id = UUID.randomUUID().toString()

        val entity = DataBatchVersionEntity(
            id = id,
            batchName = batchName,
            version = newVersion,
            sourceRuleId = sourceRuleId,
            ruleVersion = ruleVersion,
            itemsCount = itemsCount,
            ingestionJobRunId = ingestionJobRunId,
            errorCount = 0,
            validationStatus = BatchValidationStatus.PENDING.name,
            errorRate = 0.0,
            createdAt = now,
            updatedAt = now
        )
        batchVersionDao.insert(entity)

        DataBatchVersion(
            id = id,
            batchName = batchName,
            version = newVersion,
            sourceRuleId = sourceRuleId,
            ruleVersion = ruleVersion,
            itemsCount = itemsCount,
            ingestionJobRunId = ingestionJobRunId,
            errorCount = 0,
            validationStatus = BatchValidationStatus.PENDING,
            errorRate = 0.0,
            createdAt = now,
            updatedAt = now
        )
    }

    suspend fun recordLineage(
        sourceEntity: String,
        sourceId: String,
        targetEntity: String,
        targetId: String,
        batchVersionId: String,
        transformation: String? = null
    ) = withContext(dispatcher) {
        val lineage = DataLineageEntity(
            id = UUID.randomUUID().toString(),
            sourceEntity = sourceEntity,
            sourceId = sourceId,
            targetEntity = targetEntity,
            targetId = targetId,
            batchVersionId = batchVersionId,
            transformation = transformation,
            createdAt = System.currentTimeMillis()
        )
        dataLineageDao.insert(lineage)
    }

    suspend fun recordLineageBatch(lineages: List<DataLineageEntity>) = withContext(dispatcher) {
        dataLineageDao.insertAll(lineages)
    }

    private fun entityToDomain(entity: IngestionJobRunEntity): IngestionJobRun {
        return IngestionJobRun(
            id = entity.id,
            sourceRuleId = entity.sourceRuleId,
            ruleVersion = entity.ruleVersion,
            status = IngestionStatus.valueOf(entity.status),
            itemsParsed = entity.itemsParsed,
            itemsStored = entity.itemsStored,
            failureReason = entity.failureReason,
            attemptNumber = entity.attemptNumber,
            batchVersionId = entity.batchVersionId,
            startedAt = entity.startedAt,
            completedAt = entity.completedAt,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt
        )
    }

    private fun domainToEntity(run: IngestionJobRun): IngestionJobRunEntity {
        return IngestionJobRunEntity(
            id = run.id,
            sourceRuleId = run.sourceRuleId,
            ruleVersion = run.ruleVersion,
            status = run.status.name,
            itemsParsed = run.itemsParsed,
            itemsStored = run.itemsStored,
            failureReason = run.failureReason,
            attemptNumber = run.attemptNumber,
            batchVersionId = run.batchVersionId,
            startedAt = run.startedAt,
            completedAt = run.completedAt,
            createdAt = run.createdAt,
            updatedAt = run.updatedAt
        )
    }
}
