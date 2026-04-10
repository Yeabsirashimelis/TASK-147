package com.eaglepoint.storefront.domain.usecase

import com.eaglepoint.storefront.data.repository.ArticleRepository
import com.eaglepoint.storefront.data.repository.BatchValidationRepository
import com.eaglepoint.storefront.data.repository.IngestionJobRunRepository
import com.eaglepoint.storefront.domain.model.UserRole
import com.eaglepoint.storefront.security.SessionManager
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ReviewIngestionUseCaseTest {

    private lateinit var jobRunRepository: IngestionJobRunRepository
    private lateinit var batchValidationRepository: BatchValidationRepository
    private lateinit var articleRepository: ArticleRepository
    private lateinit var sessionManager: SessionManager
    private lateinit var roleGuard: RoleGuard
    private lateinit var reviewUseCase: ReviewIngestionUseCase

    @BeforeEach
    fun setUp() {
        jobRunRepository = mockk(relaxed = true)
        batchValidationRepository = mockk(relaxed = true)
        articleRepository = mockk(relaxed = true)
        sessionManager = mockk(relaxed = true)
        roleGuard = RoleGuard(sessionManager)
        reviewUseCase = ReviewIngestionUseCase(jobRunRepository, batchValidationRepository, articleRepository, roleGuard, sessionManager)
    }

    @Test
    fun `editor can view job runs`() {
        every { sessionManager.requireRole() } returns UserRole.EDITOR
        every { jobRunRepository.getAll(50, 0) } returns flowOf(emptyList())
        reviewUseCase.getJobRuns()
        verify { jobRunRepository.getAll(50, 0) }
    }

    @Test
    fun `analyst can view job runs`() {
        every { sessionManager.requireRole() } returns UserRole.ANALYST
        every { jobRunRepository.getAll(50, 0) } returns flowOf(emptyList())
        reviewUseCase.getJobRuns()
        verify { jobRunRepository.getAll(50, 0) }
    }

    @Test
    fun `regular user cannot view job runs — enforced via session`() {
        every { sessionManager.requireRole() } returns UserRole.USER
        assertThrows<SecurityException> {
            reviewUseCase.getJobRuns()
        }
    }

    @Test
    fun `editor can view batch quality history`() {
        every { sessionManager.requireRole() } returns UserRole.EDITOR
        every { batchValidationRepository.getRecentBatches(50, 0) } returns flowOf(emptyList())
        reviewUseCase.getBatchQualityHistory()
        verify { batchValidationRepository.getRecentBatches(50, 0) }
    }

    @Test
    fun `editor can view pending articles`() {
        every { sessionManager.requireRole() } returns UserRole.EDITOR
        every { articleRepository.getByCurationStatus(any(), 50, 0) } returns flowOf(emptyList())
        reviewUseCase.getPendingArticles()
        verify { articleRepository.getByCurationStatus(any(), 50, 0) }
    }

    @Test
    fun `analyst cannot view pending articles for curation — enforced via session`() {
        every { sessionManager.requireRole() } returns UserRole.ANALYST
        assertThrows<SecurityException> {
            reviewUseCase.getPendingArticles()
        }
    }

    @Test
    fun `filter job runs by source`() {
        every { sessionManager.requireRole() } returns UserRole.EDITOR
        every { jobRunRepository.getBySourceRule("src-1", 50, 0) } returns flowOf(emptyList())
        reviewUseCase.getJobRuns(sourceRuleId = "src-1")
        verify { jobRunRepository.getBySourceRule("src-1", 50, 0) }
    }

    @Test
    fun `editor can view validation errors`() {
        every { sessionManager.requireRole() } returns UserRole.EDITOR
        every { batchValidationRepository.getErrorsForBatch("batch-1") } returns flowOf(emptyList())
        reviewUseCase.getValidationErrors("batch-1")
        verify { batchValidationRepository.getErrorsForBatch("batch-1") }
    }
}
