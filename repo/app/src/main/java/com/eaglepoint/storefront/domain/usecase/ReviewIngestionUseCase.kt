package com.eaglepoint.storefront.domain.usecase

import com.eaglepoint.storefront.data.repository.ArticleRepository
import com.eaglepoint.storefront.data.repository.BatchValidationRepository
import com.eaglepoint.storefront.data.repository.IngestionJobRunRepository
import com.eaglepoint.storefront.domain.model.Article
import com.eaglepoint.storefront.domain.model.BatchQualityReport
import com.eaglepoint.storefront.domain.model.BatchValidationStatus
import com.eaglepoint.storefront.domain.model.CurationStatus
import com.eaglepoint.storefront.domain.model.DataBatchVersion
import com.eaglepoint.storefront.domain.model.IngestionJobRun
import com.eaglepoint.storefront.domain.model.UserRole
import com.eaglepoint.storefront.domain.model.ValidationError
import com.eaglepoint.storefront.security.SessionManager
import kotlinx.coroutines.flow.Flow

class ReviewIngestionUseCase(
    private val jobRunRepository: IngestionJobRunRepository,
    private val batchValidationRepository: BatchValidationRepository,
    private val articleRepository: ArticleRepository,
    private val roleGuard: RoleGuard,
    private val sessionManager: SessionManager
) {

    fun getJobRuns(
        sourceRuleId: String? = null,
        limit: Int = 50,
        offset: Int = 0
    ): Flow<List<IngestionJobRun>> {
        roleGuard.enforceIngestionConsoleAccess()
        return if (sourceRuleId != null) {
            jobRunRepository.getBySourceRule(sourceRuleId, limit, offset)
        } else {
            jobRunRepository.getAll(limit, offset)
        }
    }

    fun getBatchQualityHistory(
        limit: Int = 50,
        offset: Int = 0
    ): Flow<List<DataBatchVersion>> {
        roleGuard.enforceIngestionConsoleAccess()
        return batchValidationRepository.getRecentBatches(limit, offset)
    }

    fun getFailedBatches(): Flow<List<DataBatchVersion>> {
        roleGuard.enforceIngestionConsoleAccess()
        return batchValidationRepository.getFailedBatches()
    }

    fun getValidationErrors(batchVersionId: String): Flow<List<ValidationError>> {
        roleGuard.enforceIngestionConsoleAccess()
        return batchValidationRepository.getErrorsForBatch(batchVersionId)
    }

    fun getPendingArticles(limit: Int = 50, offset: Int = 0): Flow<List<Article>> {
        roleGuard.enforceCurationAccess()
        return articleRepository.getByCurationStatus(CurationStatus.PENDING_REVIEW, limit, offset)
    }

    fun getPendingReviewCount(): Flow<Int> {
        roleGuard.enforceCurationAccess()
        return articleRepository.getPendingReviewCount()
    }
}
