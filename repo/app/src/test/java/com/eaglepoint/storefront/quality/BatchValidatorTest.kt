package com.eaglepoint.storefront.quality

import com.eaglepoint.storefront.domain.model.BatchQualityReport
import com.eaglepoint.storefront.domain.model.BatchValidationStatus
import com.eaglepoint.storefront.domain.model.DataBatchVersion
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class BatchValidatorTest {

    private lateinit var validator: BatchValidator
    private val now = System.currentTimeMillis()

    private fun batchVersion(itemsCount: Int = 100) = DataBatchVersion(
        id = "batch-1", batchName = "test_batch", version = 1,
        sourceRuleId = "rule-1", ruleVersion = 1, itemsCount = itemsCount,
        createdAt = now, updatedAt = now
    )

    @BeforeEach
    fun setUp() {
        validator = BatchValidator()
    }

    @Test
    fun `empty batch produces passed report with zero errors`() {
        val report = validator.validate(batchVersion(0), emptyList())

        assertThat(report.passed).isTrue()
        assertThat(report.errorCount).isEqualTo(0)
        assertThat(report.errorRate).isEqualTo(0.0)
        assertThat(report.batchVersion.validationStatus).isEqualTo(BatchValidationStatus.PASSED)
    }

    @Test
    fun `all valid articles produce passed report`() {
        val items = (1..50).map {
            ValidatableItem.fromArticle(
                id = "article-$it",
                batchVersionId = "batch-1",
                publishedAt = now - (it * 24 * 60 * 60 * 1000L), // within 365 days
                title = "Article $it",
                externalUrl = "https://example.com/$it"
            )
        }

        val report = validator.validate(batchVersion(50), items)

        assertThat(report.passed).isTrue()
        assertThat(report.errorCount).isEqualTo(0)
    }

    @Test
    fun `article with publish time older than 365 days produces error`() {
        val oldDate = now - (400L * 24 * 60 * 60 * 1000L)
        val items = listOf(
            ValidatableItem.fromArticle(
                id = "old-article",
                batchVersionId = "batch-1",
                publishedAt = oldDate,
                title = "Old Article",
                externalUrl = "https://example.com/old"
            )
        )

        val report = validator.validate(batchVersion(1), items)

        assertThat(report.errorCount).isEqualTo(1)
        assertThat(report.errors[0].ruleName).isEqualTo("publish_time_range")
        assertThat(report.errors[0].fieldName).isEqualTo("publishedAt")
    }

    @Test
    fun `article with future publish time produces error`() {
        val futureDate = now + (7L * 24 * 60 * 60 * 1000L) // 7 days in future
        val items = listOf(
            ValidatableItem.fromArticle(
                id = "future-article",
                batchVersionId = "batch-1",
                publishedAt = futureDate,
                title = "Future Article",
                externalUrl = "https://example.com/future"
            )
        )

        val report = validator.validate(batchVersion(1), items)

        assertThat(report.errorCount).isEqualTo(1)
        assertThat(report.errors[0].message).contains("future")
    }

    @Test
    fun `catalog item with price below minimum produces error`() {
        val items = listOf(
            ValidatableItem.fromCatalogItem(
                id = "item-1",
                batchVersionId = "batch-1",
                price = 0.0,
                name = "Free Item"
            )
        )

        val report = validator.validate(batchVersion(1), items)

        assertThat(report.errorCount).isEqualTo(1)
        assertThat(report.errors[0].ruleName).isEqualTo("price_range")
        assertThat(report.errors[0].fieldName).isEqualTo("price")
    }

    @Test
    fun `catalog item with price above maximum produces error`() {
        val items = listOf(
            ValidatableItem.fromCatalogItem(
                id = "item-1",
                batchVersionId = "batch-1",
                price = 10000.00,
                name = "Expensive Item"
            )
        )

        val report = validator.validate(batchVersion(1), items)

        assertThat(report.errorCount).isEqualTo(1)
        assertThat(report.errors[0].ruleName).isEqualTo("price_range")
    }

    @Test
    fun `catalog item with valid price passes`() {
        val items = listOf(
            ValidatableItem.fromCatalogItem(
                id = "item-1",
                batchVersionId = "batch-1",
                price = 49.99,
                name = "Normal Item"
            )
        )

        val report = validator.validate(batchVersion(1), items)

        assertThat(report.errorCount).isEqualTo(0)
    }

    @Test
    fun `catalog item with boundary prices pass`() {
        val items = listOf(
            ValidatableItem.fromCatalogItem("min", "batch-1", price = 0.01, name = "Min"),
            ValidatableItem.fromCatalogItem("max", "batch-1", price = 9999.99, name = "Max")
        )

        val report = validator.validate(batchVersion(2), items)

        assertThat(report.errorCount).isEqualTo(0)
    }

    @Test
    fun `inventory snapshot with negative quantity produces error`() {
        val items = listOf(
            ValidatableItem.fromInventorySnapshot(
                id = "snap-1",
                batchVersionId = "batch-1",
                quantity = -5,
                catalogItemId = "item-1"
            )
        )

        val report = validator.validate(batchVersion(1), items)

        assertThat(report.errorCount).isEqualTo(1)
        assertThat(report.errors[0].ruleName).isEqualTo("inventory_non_negative")
        assertThat(report.errors[0].fieldName).isEqualTo("quantity")
        assertThat(report.errors[0].actualValue).isEqualTo("-5")
    }

    @Test
    fun `inventory snapshot with zero quantity passes`() {
        val items = listOf(
            ValidatableItem.fromInventorySnapshot(
                id = "snap-1",
                batchVersionId = "batch-1",
                quantity = 0,
                catalogItemId = "item-1"
            )
        )

        val report = validator.validate(batchVersion(1), items)

        assertThat(report.errorCount).isEqualTo(0)
    }

    @Test
    fun `error rate above 2 percent fails batch`() {
        // 3 errors in 100 items = 3% error rate
        val items = (1..100).map { i ->
            if (i <= 3) {
                ValidatableItem.fromCatalogItem(
                    id = "item-$i",
                    batchVersionId = "batch-1",
                    price = -1.0, // invalid
                    name = "Bad Item $i"
                )
            } else {
                ValidatableItem.fromCatalogItem(
                    id = "item-$i",
                    batchVersionId = "batch-1",
                    price = 9.99,
                    name = "Good Item $i"
                )
            }
        }

        val report = validator.validate(batchVersion(100), items)

        assertThat(report.passed).isFalse()
        assertThat(report.errorRate).isGreaterThan(BatchQualityReport.ERROR_RATE_THRESHOLD)
        assertThat(report.batchVersion.validationStatus).isEqualTo(BatchValidationStatus.FAILED)
    }

    @Test
    fun `error rate at exactly 2 percent passes batch`() {
        // 2 errors in 100 items = 2% error rate
        val items = (1..100).map { i ->
            if (i <= 2) {
                ValidatableItem.fromInventorySnapshot(
                    id = "snap-$i",
                    batchVersionId = "batch-1",
                    quantity = -1,
                    catalogItemId = "item-$i"
                )
            } else {
                ValidatableItem.fromInventorySnapshot(
                    id = "snap-$i",
                    batchVersionId = "batch-1",
                    quantity = 10,
                    catalogItemId = "item-$i"
                )
            }
        }

        val report = validator.validate(batchVersion(100), items)

        assertThat(report.passed).isTrue()
        assertThat(report.errorRate).isAtMost(BatchQualityReport.ERROR_RATE_THRESHOLD)
    }

    @Test
    fun `errors are grouped by rule name`() {
        val items = listOf(
            ValidatableItem.fromCatalogItem("i1", "batch-1", price = -1.0, name = "Bad"),
            ValidatableItem.fromCatalogItem("i2", "batch-1", price = 99999.0, name = "Bad"),
            ValidatableItem.fromInventorySnapshot("s1", "batch-1", quantity = -10, catalogItemId = "i1")
        )

        val report = validator.validate(batchVersion(3), items)

        assertThat(report.errorsByRule).containsKey("price_range")
        assertThat(report.errorsByRule["price_range"]).isEqualTo(2)
        assertThat(report.errorsByRule).containsKey("inventory_non_negative")
        assertThat(report.errorsByRule["inventory_non_negative"]).isEqualTo(1)
    }

    @Test
    fun `errors are grouped by entity type and field`() {
        val items = listOf(
            ValidatableItem.fromCatalogItem("i1", "batch-1", price = -1.0, name = "Bad"),
            ValidatableItem.fromInventorySnapshot("s1", "batch-1", quantity = -10, catalogItemId = "i1")
        )

        val report = validator.validate(batchVersion(2), items)

        assertThat(report.errorsByField).containsKey("catalog_item.price")
        assertThat(report.errorsByField).containsKey("inventory_snapshot.quantity")
    }

    @Test
    fun `mixed entity types validated correctly`() {
        val items = listOf(
            ValidatableItem.fromArticle("a1", "batch-1", publishedAt = now - 1000, title = "Good", externalUrl = "https://x.com/1"),
            ValidatableItem.fromCatalogItem("c1", "batch-1", price = 29.99, name = "Good"),
            ValidatableItem.fromInventorySnapshot("s1", "batch-1", quantity = 50, catalogItemId = "c1")
        )

        val report = validator.validate(batchVersion(3), items)

        assertThat(report.passed).isTrue()
        assertThat(report.errorCount).isEqualTo(0)
    }

    @Test
    fun `rules only apply to matching entity types`() {
        // Price rule should not flag articles, publish time rule should not flag catalog items
        val items = listOf(
            ValidatableItem.fromArticle("a1", "batch-1", publishedAt = null, title = "Good", externalUrl = "https://x.com/1"),
            ValidatableItem.fromCatalogItem("c1", "batch-1", price = null, name = "No Price")
        )

        val report = validator.validate(batchVersion(2), items)

        assertThat(report.errorCount).isEqualTo(0)
    }
}
