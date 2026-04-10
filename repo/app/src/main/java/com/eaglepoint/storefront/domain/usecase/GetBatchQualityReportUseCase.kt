package com.eaglepoint.storefront.domain.usecase

import com.eaglepoint.storefront.data.repository.BatchValidationRepository
import com.eaglepoint.storefront.domain.model.BatchQualityReport
import com.eaglepoint.storefront.domain.model.BatchValidationStatus
import com.eaglepoint.storefront.domain.model.DataBatchVersion
import com.eaglepoint.storefront.domain.model.ValidationError
import kotlinx.coroutines.flow.Flow

class GetBatchQualityReportUseCase(
    private val batchValidationRepository: BatchValidationRepository
) {

    fun getRecentBatches(limit: Int = 50, offset: Int = 0): Flow<List<DataBatchVersion>> {
        return batchValidationRepository.getRecentBatches(limit, offset)
    }

    fun getFailedBatches(limit: Int = 20): Flow<List<DataBatchVersion>> {
        return batchValidationRepository.getFailedBatches(limit)
    }

    fun getBatchesByStatus(status: BatchValidationStatus): Flow<List<DataBatchVersion>> {
        return batchValidationRepository.getBatchesByStatus(status)
    }

    fun getErrorsForBatch(batchVersionId: String): Flow<List<ValidationError>> {
        return batchValidationRepository.getErrorsForBatch(batchVersionId)
    }

    suspend fun getReport(batchVersionId: String): BatchQualityReport? {
        val batch = batchValidationRepository.findBatchById(batchVersionId) ?: return null
        val errors = batchValidationRepository.getErrorsForBatchList(batchVersionId)

        val errorsByRule = errors.groupBy { it.ruleName }.mapValues { it.value.size }
        val errorsByField = errors.groupBy { "${it.entityType}.${it.fieldName}" }.mapValues { it.value.size }

        return BatchQualityReport(
            batchVersion = batch,
            totalItems = batch.itemsCount,
            errorCount = batch.errorCount,
            errorRate = batch.errorRate,
            passed = batch.validationStatus == BatchValidationStatus.PASSED,
            errors = errors,
            errorsByRule = errorsByRule,
            errorsByField = errorsByField
        )
    }
}
