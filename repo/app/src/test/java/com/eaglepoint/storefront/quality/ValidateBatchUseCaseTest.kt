package com.eaglepoint.storefront.quality

import com.eaglepoint.storefront.data.repository.ArticleRepository
import com.eaglepoint.storefront.data.repository.BatchValidationRepository
import com.eaglepoint.storefront.data.repository.CatalogRepository
import com.eaglepoint.storefront.data.repository.IngestionAlertRepository
import com.eaglepoint.storefront.domain.model.Article
import com.eaglepoint.storefront.domain.model.AuditAction
import com.eaglepoint.storefront.domain.model.BatchValidationStatus
import com.eaglepoint.storefront.domain.model.DataBatchVersion
import com.eaglepoint.storefront.domain.usecase.LogAuditEventUseCase
import com.eaglepoint.storefront.domain.usecase.ValidateBatchUseCase
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ValidateBatchUseCaseTest {

    private lateinit var batchValidationRepository: BatchValidationRepository
    private lateinit var articleRepository: ArticleRepository
    private lateinit var alertRepository: IngestionAlertRepository
    private lateinit var batchValidator: BatchValidator
    private lateinit var logAuditEvent: LogAuditEventUseCase
    private lateinit var catalogRepository: CatalogRepository
    private lateinit var validateBatchUseCase: ValidateBatchUseCase

    private val testDispatcher = StandardTestDispatcher()
    private val now = System.currentTimeMillis()

    private val testBatch = DataBatchVersion(
        id = "batch-1", batchName = "ingestion_rule-1", version = 1,
        sourceRuleId = "rule-1", ruleVersion = 1, itemsCount = 3,
        createdAt = now, updatedAt = now
    )

    @BeforeEach
    fun setUp() {
        batchValidationRepository = mockk(relaxed = true)
        articleRepository = mockk(relaxed = true)
        alertRepository = mockk(relaxed = true)
        batchValidator = BatchValidator()
        logAuditEvent = mockk(relaxed = true)
        catalogRepository = mockk(relaxed = true)

        coEvery { catalogRepository.getByBatchVersionId(any()) } returns emptyList()
        coEvery { catalogRepository.getInventorySnapshotsByBatchVersionId(any()) } returns emptyList()

        validateBatchUseCase = ValidateBatchUseCase(
            batchValidationRepository, articleRepository, alertRepository,
            batchValidator, logAuditEvent, catalogRepository, testDispatcher
        )
    }

    private fun testArticle(id: String, publishedAt: Long? = now - 1000) = Article(
        id = id, sourceRuleId = "rule-1", batchVersionId = "batch-1",
        title = "Article $id", externalUrl = "https://example.com/$id",
        publishedAt = publishedAt, createdAt = now, updatedAt = now
    )

    @Test
    fun `valid batch produces passed report`() = runTest(testDispatcher) {
        val articles = listOf(
            testArticle("a1"),
            testArticle("a2"),
            testArticle("a3")
        )
        coEvery { articleRepository.getBySourceRule("rule-1") } returns flowOf(articles)

        val report = validateBatchUseCase(testBatch, "admin")

        assertThat(report.passed).isTrue()
        assertThat(report.errorCount).isEqualTo(0)
    }

    @Test
    fun `batch with old articles produces errors`() = runTest(testDispatcher) {
        val oldDate = now - (400L * 24 * 60 * 60 * 1000L)
        val articles = listOf(
            testArticle("a1", publishedAt = oldDate),
            testArticle("a2"),
            testArticle("a3")
        )
        coEvery { articleRepository.getBySourceRule("rule-1") } returns flowOf(articles)

        val report = validateBatchUseCase(testBatch, "admin")

        assertThat(report.errorCount).isEqualTo(1)
        assertThat(report.errors[0].ruleName).isEqualTo("publish_time_range")
    }

    @Test
    fun `validation errors are persisted`() = runTest(testDispatcher) {
        val oldDate = now - (400L * 24 * 60 * 60 * 1000L)
        val articles = listOf(testArticle("a1", publishedAt = oldDate))
        coEvery { articleRepository.getBySourceRule("rule-1") } returns flowOf(articles)

        validateBatchUseCase(testBatch, "admin")

        coVerify { batchValidationRepository.clearErrorsForBatch("batch-1") }
        coVerify { batchValidationRepository.saveValidationErrors(any()) }
    }

    @Test
    fun `batch version is updated with validation results`() = runTest(testDispatcher) {
        val articles = listOf(testArticle("a1"))
        coEvery { articleRepository.getBySourceRule("rule-1") } returns flowOf(articles)

        validateBatchUseCase(testBatch, "admin")

        coVerify { batchValidationRepository.updateBatchValidation(any()) }
    }

    @Test
    fun `passed batch logs BATCH_VALIDATION_PASSED`() = runTest(testDispatcher) {
        val articles = listOf(testArticle("a1"))
        coEvery { articleRepository.getBySourceRule("rule-1") } returns flowOf(articles)

        validateBatchUseCase(testBatch, "admin")

        coVerify {
            logAuditEvent(
                userId = "admin",
                action = AuditAction.BATCH_VALIDATION_PASSED,
                target = "data_batch_version",
                targetId = "batch-1",
                detail = any()
            )
        }
    }

    @Test
    fun `failed batch logs BATCH_VALIDATION_FAILED and generates alert`() = runTest(testDispatcher) {
        // All 3 articles have old dates = 100% error rate
        val oldDate = now - (400L * 24 * 60 * 60 * 1000L)
        val articles = listOf(
            testArticle("a1", publishedAt = oldDate),
            testArticle("a2", publishedAt = oldDate),
            testArticle("a3", publishedAt = oldDate)
        )
        coEvery { articleRepository.getBySourceRule("rule-1") } returns flowOf(articles)

        val report = validateBatchUseCase(testBatch, "admin")

        assertThat(report.passed).isFalse()
        coVerify {
            logAuditEvent(
                userId = "admin",
                action = AuditAction.BATCH_VALIDATION_FAILED,
                target = "data_batch_version",
                targetId = "batch-1",
                detail = any()
            )
        }
        coVerify { alertRepository.create(any()) }
        coVerify {
            logAuditEvent(
                userId = "admin",
                action = AuditAction.QUALITY_ALERT_GENERATED,
                target = any(),
                targetId = any(),
                detail = any()
            )
        }
    }

    @Test
    fun `only articles matching batch version id are validated`() = runTest(testDispatcher) {
        val articles = listOf(
            testArticle("a1").copy(batchVersionId = "batch-1"),
            testArticle("a2").copy(batchVersionId = "other-batch") // different batch
        )
        coEvery { articleRepository.getBySourceRule("rule-1") } returns flowOf(articles)

        val report = validateBatchUseCase(testBatch, "admin")

        // Only 1 article should be validated (the one matching batch-1)
        assertThat(report.totalItems).isEqualTo(1)
    }
}
