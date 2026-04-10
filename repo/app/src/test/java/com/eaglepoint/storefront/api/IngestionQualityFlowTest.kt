package com.eaglepoint.storefront.api

import com.eaglepoint.storefront.data.repository.ArticleRepository
import com.eaglepoint.storefront.data.repository.BatchValidationRepository
import com.eaglepoint.storefront.data.repository.IngestionAlertRepository
import com.eaglepoint.storefront.data.repository.IngestionJobRunRepository
import com.eaglepoint.storefront.data.repository.SourceRuleRepository
import com.eaglepoint.storefront.domain.model.BatchQualityReport
import com.eaglepoint.storefront.domain.model.DataBatchVersion
import com.eaglepoint.storefront.domain.model.FeedType
import com.eaglepoint.storefront.domain.model.IngestionJobRun
import com.eaglepoint.storefront.domain.model.IngestionStatus
import com.eaglepoint.storefront.domain.model.SourceRule
import com.eaglepoint.storefront.domain.model.UserRole
import com.eaglepoint.storefront.domain.usecase.LogAuditEventUseCase
import com.eaglepoint.storefront.domain.usecase.ReviewIngestionUseCase
import com.eaglepoint.storefront.domain.usecase.RoleGuard
import com.eaglepoint.storefront.domain.usecase.RunIngestionUseCase
import com.eaglepoint.storefront.domain.usecase.SaveSourceRuleUseCase
import com.eaglepoint.storefront.domain.validation.InputValidator
import com.eaglepoint.storefront.ingestion.IngestionEngine
import com.eaglepoint.storefront.security.SessionManager
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * API-level functional tests for ingestion and quality validation flows.
 * Tests source rule CRUD, ingestion execution, quality validation results,
 * and role-based access to the review interface — all enforced via session.
 */
class IngestionQualityFlowTest {

    private lateinit var sourceRuleRepository: SourceRuleRepository
    private lateinit var jobRunRepository: IngestionJobRunRepository
    private lateinit var batchValidationRepository: BatchValidationRepository
    private lateinit var articleRepository: ArticleRepository
    private lateinit var ingestionEngine: IngestionEngine
    private lateinit var logAuditEvent: LogAuditEventUseCase
    private lateinit var sessionManager: SessionManager
    private lateinit var roleGuard: RoleGuard

    private lateinit var saveSourceRuleUseCase: SaveSourceRuleUseCase
    private lateinit var runIngestionUseCase: RunIngestionUseCase
    private lateinit var reviewIngestionUseCase: ReviewIngestionUseCase

    private val now = System.currentTimeMillis()
    private val testRule = SourceRule(
        id = "rule-1", name = "Test Source", url = "https://example.com/feed.xml",
        feedType = FeedType.RSS, createdAt = now, updatedAt = now
    )

    @BeforeEach
    fun setUp() {
        sourceRuleRepository = mockk(relaxed = true)
        jobRunRepository = mockk(relaxed = true)
        batchValidationRepository = mockk(relaxed = true)
        articleRepository = mockk(relaxed = true)
        ingestionEngine = mockk(relaxed = true)
        logAuditEvent = mockk(relaxed = true)
        sessionManager = mockk(relaxed = true)
        roleGuard = RoleGuard(sessionManager)

        // Default session: ADMIN
        every { sessionManager.requireRole() } returns UserRole.ADMIN
        every { sessionManager.requireUserId() } returns "admin-1"

        saveSourceRuleUseCase = SaveSourceRuleUseCase(sourceRuleRepository, InputValidator(), logAuditEvent, roleGuard, sessionManager)
        runIngestionUseCase = RunIngestionUseCase(ingestionEngine, sourceRuleRepository, sessionManager)
        reviewIngestionUseCase = ReviewIngestionUseCase(jobRunRepository, batchValidationRepository, articleRepository, roleGuard, sessionManager)
    }

    // --- Source rule: normal creation ---
    @Test
    fun `create source rule with valid data succeeds`() = runTest {
        coEvery { sourceRuleRepository.findById("rule-1") } returns null

        val result = saveSourceRuleUseCase(testRule)

        assertThat(result.isSuccess).isTrue()
        coVerify { sourceRuleRepository.save(any()) }
    }

    // --- Source rule: invalid URL ---
    @Test
    fun `create source rule with invalid URL fails`() = runTest {
        val badRule = testRule.copy(url = "not-a-url")
        val result = saveSourceRuleUseCase(badRule)
        assertThat(result.isFailure).isTrue()
    }

    // --- Source rule: blank name ---
    @Test
    fun `create source rule with blank name fails`() = runTest {
        val badRule = testRule.copy(name = "")
        val result = saveSourceRuleUseCase(badRule)
        assertThat(result.isFailure).isTrue()
    }

    // --- Ingestion: source not found ---
    @Test
    fun `run ingestion for non-existent source fails`() = runTest {
        coEvery { sourceRuleRepository.findById("missing") } returns null

        val result = runIngestionUseCase.runForSource("missing")

        assertThat(result.isFailure).isTrue()
    }

    // --- Ingestion: inactive source ---
    @Test
    fun `run ingestion for inactive source fails`() = runTest {
        coEvery { sourceRuleRepository.findById("rule-1") } returns testRule.copy(isActive = false)

        val result = runIngestionUseCase.runForSource("rule-1")

        assertThat(result.isFailure).isTrue()
    }

    // --- Ingestion: success ---
    @Test
    fun `run ingestion for active source succeeds`() = runTest {
        val successRun = IngestionJobRun(
            id = "run-1", sourceRuleId = "rule-1", ruleVersion = 1,
            status = IngestionStatus.SUCCESS, itemsParsed = 10, itemsStored = 8,
            startedAt = now, createdAt = now, updatedAt = now
        )
        coEvery { sourceRuleRepository.findById("rule-1") } returns testRule
        coEvery { ingestionEngine.runIngestionForSource(testRule, "admin-1") } returns successRun

        val result = runIngestionUseCase.runForSource("rule-1")

        assertThat(result.isSuccess).isTrue()
        assertThat(result.getOrNull()?.status).isEqualTo(IngestionStatus.SUCCESS)
    }

    // --- Review: role-based access via session ---
    @Test
    fun `editor can access ingestion review`() {
        every { sessionManager.requireRole() } returns UserRole.EDITOR
        every { jobRunRepository.getAll(50, 0) } returns flowOf(emptyList())
        reviewIngestionUseCase.getJobRuns()
    }

    @Test
    fun `analyst can access ingestion review`() {
        every { sessionManager.requireRole() } returns UserRole.ANALYST
        every { jobRunRepository.getAll(50, 0) } returns flowOf(emptyList())
        reviewIngestionUseCase.getJobRuns()
    }

    @Test
    fun `regular user cannot access ingestion review`() {
        every { sessionManager.requireRole() } returns UserRole.USER
        assertThrows<SecurityException> {
            reviewIngestionUseCase.getJobRuns()
        }
    }

    // --- Review: pending articles access control ---
    @Test
    fun `analyst cannot access pending articles for curation`() {
        every { sessionManager.requireRole() } returns UserRole.ANALYST
        assertThrows<SecurityException> {
            reviewIngestionUseCase.getPendingArticles()
        }
    }

    @Test
    fun `editor can access pending articles`() {
        every { sessionManager.requireRole() } returns UserRole.EDITOR
        every { articleRepository.getByCurationStatus(any(), 50, 0) } returns flowOf(emptyList())
        reviewIngestionUseCase.getPendingArticles()
    }

    // --- Source rule update bumps version ---
    @Test
    fun `updating existing source rule increments rule version`() = runTest {
        coEvery { sourceRuleRepository.findById("rule-1") } returns testRule.copy(ruleVersion = 3)

        val result = saveSourceRuleUseCase(testRule)

        assertThat(result.isSuccess).isTrue()
        assertThat(result.getOrNull()?.ruleVersion).isEqualTo(4)
    }
}
