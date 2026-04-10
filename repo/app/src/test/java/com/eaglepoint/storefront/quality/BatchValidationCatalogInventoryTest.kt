package com.eaglepoint.storefront.quality

import com.eaglepoint.storefront.data.repository.ArticleRepository
import com.eaglepoint.storefront.data.repository.BatchValidationRepository
import com.eaglepoint.storefront.data.repository.CatalogRepository
import com.eaglepoint.storefront.data.repository.IngestionAlertRepository
import com.eaglepoint.storefront.domain.model.CatalogItem
import com.eaglepoint.storefront.domain.model.DataBatchVersion
import com.eaglepoint.storefront.domain.model.InventorySnapshot
import com.eaglepoint.storefront.domain.usecase.LogAuditEventUseCase
import com.eaglepoint.storefront.domain.usecase.ValidateBatchUseCase
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class BatchValidationCatalogInventoryTest {

    private lateinit var batchValidationRepository: BatchValidationRepository
    private lateinit var articleRepository: ArticleRepository
    private lateinit var alertRepository: IngestionAlertRepository
    private lateinit var catalogRepository: CatalogRepository
    private lateinit var batchValidator: BatchValidator
    private lateinit var logAuditEvent: LogAuditEventUseCase
    private lateinit var validateBatchUseCase: ValidateBatchUseCase

    private val testDispatcher = StandardTestDispatcher()
    private val now = System.currentTimeMillis()

    private val testBatch = DataBatchVersion(
        id = "batch-1", batchName = "test_batch", version = 1,
        sourceRuleId = "rule-1", ruleVersion = 1, itemsCount = 5,
        createdAt = now, updatedAt = now
    )

    @BeforeEach
    fun setUp() {
        batchValidationRepository = mockk(relaxed = true)
        articleRepository = mockk(relaxed = true)
        alertRepository = mockk(relaxed = true)
        catalogRepository = mockk(relaxed = true)
        batchValidator = BatchValidator()
        logAuditEvent = mockk(relaxed = true)

        coEvery { articleRepository.getBySourceRule(any()) } returns flowOf(emptyList())
        coEvery { catalogRepository.getByBatchVersionId(any()) } returns emptyList()
        coEvery { catalogRepository.getInventorySnapshotsByBatchVersionId(any()) } returns emptyList()

        validateBatchUseCase = ValidateBatchUseCase(
            batchValidationRepository, articleRepository, alertRepository,
            batchValidator, logAuditEvent, catalogRepository, testDispatcher
        )
    }

    @Test
    fun `validates catalog items for price range`() = runTest(testDispatcher) {
        val catalogItems = listOf(
            CatalogItem(id = "c1", sku = "SKU-1", name = "Jersey", price = 49.99),
            CatalogItem(id = "c2", sku = "SKU-2", name = "Free item", price = 0.0), // below min
            CatalogItem(id = "c3", sku = "SKU-3", name = "Expensive", price = 99999.0) // above max
        )
        coEvery { catalogRepository.getByBatchVersionId("batch-1") } returns catalogItems

        val report = validateBatchUseCase(testBatch, "admin")

        // c2 has price 0.0 (below 0.01 min) and c3 has price 99999 (above 9999.99 max)
        val priceErrors = report.errors.filter { it.ruleName == "price_range" }
        assertThat(priceErrors).hasSize(2)
    }

    @Test
    fun `validates inventory snapshots for non-negative quantity`() = runTest(testDispatcher) {
        val snapshots = listOf(
            InventorySnapshot(id = "s1", catalogItemId = "c1", quantity = 10, createdAt = now, updatedAt = now, batchVersionId = "batch-1"),
            InventorySnapshot(id = "s2", catalogItemId = "c2", quantity = -5, createdAt = now, updatedAt = now, batchVersionId = "batch-1"),
            InventorySnapshot(id = "s3", catalogItemId = "c3", quantity = 0, createdAt = now, updatedAt = now, batchVersionId = "batch-1")
        )
        coEvery { catalogRepository.getInventorySnapshotsByBatchVersionId("batch-1") } returns snapshots

        val report = validateBatchUseCase(testBatch, "admin")

        val inventoryErrors = report.errors.filter { it.ruleName == "inventory_non_negative" }
        assertThat(inventoryErrors).hasSize(1) // only s2 with negative quantity
        assertThat(inventoryErrors[0].entityId).isEqualTo("s2")
    }

    @Test
    fun `validates all entity types together`() = runTest(testDispatcher) {
        val oldDate = now - (400L * 24 * 60 * 60 * 1000L)
        val articles = listOf(
            com.eaglepoint.storefront.domain.model.Article(
                id = "a1", sourceRuleId = "rule-1", batchVersionId = "batch-1",
                title = "Old article", externalUrl = "https://example.com/old",
                publishedAt = oldDate, createdAt = now, updatedAt = now
            )
        )
        val catalogItems = listOf(
            CatalogItem(id = "c1", sku = "SKU-BAD", name = "Bad Price", price = -1.0)
        )
        val snapshots = listOf(
            InventorySnapshot(id = "s1", catalogItemId = "c1", quantity = -3, createdAt = now, updatedAt = now, batchVersionId = "batch-1")
        )

        coEvery { articleRepository.getBySourceRule("rule-1") } returns flowOf(articles)
        coEvery { catalogRepository.getByBatchVersionId("batch-1") } returns catalogItems
        coEvery { catalogRepository.getInventorySnapshotsByBatchVersionId("batch-1") } returns snapshots

        val report = validateBatchUseCase(testBatch, "admin")

        // 1 article publish_time error + 1 price_range error + 1 inventory error = 3 errors
        assertThat(report.errorCount).isEqualTo(3)
        assertThat(report.totalItems).isEqualTo(3)
    }

    @Test
    fun `valid catalog and inventory produces no errors`() = runTest(testDispatcher) {
        val catalogItems = listOf(
            CatalogItem(id = "c1", sku = "SKU-1", name = "Good Item", price = 29.99)
        )
        val snapshots = listOf(
            InventorySnapshot(id = "s1", catalogItemId = "c1", quantity = 50, createdAt = now, updatedAt = now, batchVersionId = "batch-1")
        )
        coEvery { catalogRepository.getByBatchVersionId("batch-1") } returns catalogItems
        coEvery { catalogRepository.getInventorySnapshotsByBatchVersionId("batch-1") } returns snapshots

        val report = validateBatchUseCase(testBatch, "admin")

        assertThat(report.passed).isTrue()
        assertThat(report.errorCount).isEqualTo(0)
    }
}
