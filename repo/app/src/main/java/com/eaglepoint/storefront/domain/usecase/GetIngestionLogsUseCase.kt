package com.eaglepoint.storefront.domain.usecase

import com.eaglepoint.storefront.data.repository.IngestionJobRunRepository
import com.eaglepoint.storefront.domain.model.IngestionJobRun
import kotlinx.coroutines.flow.Flow

class GetIngestionLogsUseCase(
    private val jobRunRepository: IngestionJobRunRepository
) {

    fun getAll(limit: Int = 50, offset: Int = 0): Flow<List<IngestionJobRun>> {
        return jobRunRepository.getAll(limit, offset)
    }

    fun getBySourceRule(sourceRuleId: String, limit: Int = 50, offset: Int = 0): Flow<List<IngestionJobRun>> {
        return jobRunRepository.getBySourceRule(sourceRuleId, limit, offset)
    }

    suspend fun getLatestForSource(sourceRuleId: String): IngestionJobRun? {
        return jobRunRepository.getLatestForSource(sourceRuleId)
    }
}
