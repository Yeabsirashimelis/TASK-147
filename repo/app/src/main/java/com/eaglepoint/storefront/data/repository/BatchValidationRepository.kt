package com.eaglepoint.storefront.data.repository

import com.eaglepoint.storefront.data.db.dao.DataBatchVersionDao
import com.eaglepoint.storefront.data.db.dao.ValidationErrorDao
import com.eaglepoint.storefront.data.db.entity.DataBatchVersionEntity
import com.eaglepoint.storefront.data.db.entity.ValidationErrorEntity
import com.eaglepoint.storefront.domain.model.BatchValidationStatus
import com.eaglepoint.storefront.domain.model.DataBatchVersion
import com.eaglepoint.storefront.domain.model.ValidationError
import com.eaglepoint.storefront.domain.model.ValidationSeverity
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class BatchValidationRepository(
    private val batchVersionDao: DataBatchVersionDao,
    private val validationErrorDao: ValidationErrorDao,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    suspend fun updateBatchValidation(batch: DataBatchVersion) = withContext(dispatcher) {
        val entity = batchVersionDao.findById(batch.id)
            ?: throw IllegalArgumentException("Batch version not found: ${batch.id}")
        batchVersionDao.update(
            entity.copy(
                errorCount = batch.errorCount,
                validationStatus = batch.validationStatus?.name,
                errorRate = batch.errorRate,
                updatedAt = batch.updatedAt
            )
        )
    }

    suspend fun saveValidationErrors(errors: List<ValidationError>) = withContext(dispatcher) {
        val entities = errors.map { errorToEntity(it) }
        validationErrorDao.insertAll(entities)
    }

    suspend fun clearErrorsForBatch(batchVersionId: String) = withContext(dispatcher) {
        validationErrorDao.deleteByBatchVersion(batchVersionId)
    }

    fun getErrorsForBatch(batchVersionId: String): Flow<List<ValidationError>> {
        return validationErrorDao.getByBatchVersion(batchVersionId).map { entities ->
            entities.map { entityToError(it) }
        }
    }

    suspend fun getErrorsForBatchList(batchVersionId: String): List<ValidationError> =
        withContext(dispatcher) {
            validationErrorDao.getByBatchVersionList(batchVersionId).map { entityToError(it) }
        }

    suspend fun getErrorCountForBatch(batchVersionId: String): Int = withContext(dispatcher) {
        validationErrorDao.countByBatchVersion(batchVersionId)
    }

    fun getRecentBatches(limit: Int = 50, offset: Int = 0): Flow<List<DataBatchVersion>> {
        return batchVersionDao.getRecent(limit, offset).map { entities ->
            entities.map { batchEntityToDomain(it) }
        }
    }

    fun getFailedBatches(limit: Int = 20): Flow<List<DataBatchVersion>> {
        return batchVersionDao.getRecentFailed(limit).map { entities ->
            entities.map { batchEntityToDomain(it) }
        }
    }

    fun getBatchesByStatus(status: BatchValidationStatus): Flow<List<DataBatchVersion>> {
        return batchVersionDao.getByValidationStatus(status.name).map { entities ->
            entities.map { batchEntityToDomain(it) }
        }
    }

    suspend fun findBatchById(id: String): DataBatchVersion? = withContext(dispatcher) {
        batchVersionDao.findById(id)?.let { batchEntityToDomain(it) }
    }

    private fun batchEntityToDomain(entity: DataBatchVersionEntity): DataBatchVersion {
        return DataBatchVersion(
            id = entity.id,
            batchName = entity.batchName,
            version = entity.version,
            sourceRuleId = entity.sourceRuleId,
            ruleVersion = entity.ruleVersion,
            itemsCount = entity.itemsCount,
            ingestionJobRunId = entity.ingestionJobRunId,
            errorCount = entity.errorCount,
            validationStatus = entity.validationStatus?.let {
                try { BatchValidationStatus.valueOf(it) } catch (e: Exception) { null }
            },
            errorRate = entity.errorRate,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt
        )
    }

    private fun errorToEntity(error: ValidationError): ValidationErrorEntity {
        return ValidationErrorEntity(
            id = error.id,
            batchVersionId = error.batchVersionId,
            entityType = error.entityType,
            entityId = error.entityId,
            fieldName = error.fieldName,
            ruleName = error.ruleName,
            message = error.message,
            actualValue = error.actualValue,
            severity = error.severity.name,
            createdAt = error.createdAt
        )
    }

    private fun entityToError(entity: ValidationErrorEntity): ValidationError {
        return ValidationError(
            id = entity.id,
            batchVersionId = entity.batchVersionId,
            entityType = entity.entityType,
            entityId = entity.entityId,
            fieldName = entity.fieldName,
            ruleName = entity.ruleName,
            message = entity.message,
            actualValue = entity.actualValue,
            severity = try { ValidationSeverity.valueOf(entity.severity) } catch (e: Exception) { ValidationSeverity.ERROR },
            createdAt = entity.createdAt
        )
    }
}
