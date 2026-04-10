package com.eaglepoint.storefront.quality

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class ValidationRuleTest {

    private val now = System.currentTimeMillis()

    // PublishTimeRule tests

    @Test
    fun `PublishTimeRule passes article within range`() {
        val rule = PublishTimeRule()
        val item = ValidatableItem.fromArticle(
            id = "a1", batchVersionId = "b1",
            publishedAt = now - (30L * 24 * 60 * 60 * 1000), // 30 days ago
            title = "Recent", externalUrl = "https://x.com"
        )

        val errors = rule.validate(item)

        assertThat(errors).isEmpty()
    }

    @Test
    fun `PublishTimeRule fails article older than max age`() {
        val rule = PublishTimeRule(maxAgeDays = 30)
        val item = ValidatableItem.fromArticle(
            id = "a1", batchVersionId = "b1",
            publishedAt = now - (60L * 24 * 60 * 60 * 1000), // 60 days ago
            title = "Old", externalUrl = "https://x.com"
        )

        val errors = rule.validate(item)

        assertThat(errors).hasSize(1)
        assertThat(errors[0].message).contains("30 days")
    }

    @Test
    fun `PublishTimeRule ignores non-article items`() {
        val rule = PublishTimeRule()
        val item = ValidatableItem.fromCatalogItem(
            id = "c1", batchVersionId = "b1", price = 10.0, name = "Item"
        )

        val errors = rule.validate(item)

        assertThat(errors).isEmpty()
    }

    @Test
    fun `PublishTimeRule ignores null publish time`() {
        val rule = PublishTimeRule()
        val item = ValidatableItem.fromArticle(
            id = "a1", batchVersionId = "b1",
            publishedAt = null,
            title = "No Date", externalUrl = "https://x.com"
        )

        val errors = rule.validate(item)

        assertThat(errors).isEmpty()
    }

    // PriceRangeRule tests

    @Test
    fun `PriceRangeRule passes valid price`() {
        val rule = PriceRangeRule()
        val item = ValidatableItem.fromCatalogItem(
            id = "c1", batchVersionId = "b1", price = 49.99, name = "Item"
        )

        val errors = rule.validate(item)

        assertThat(errors).isEmpty()
    }

    @Test
    fun `PriceRangeRule fails price below minimum`() {
        val rule = PriceRangeRule()
        val item = ValidatableItem.fromCatalogItem(
            id = "c1", batchVersionId = "b1", price = 0.001, name = "Item"
        )

        val errors = rule.validate(item)

        assertThat(errors).hasSize(1)
    }

    @Test
    fun `PriceRangeRule fails negative price`() {
        val rule = PriceRangeRule()
        val item = ValidatableItem.fromCatalogItem(
            id = "c1", batchVersionId = "b1", price = -5.0, name = "Item"
        )

        val errors = rule.validate(item)

        assertThat(errors).hasSize(1)
        assertThat(errors[0].actualValue).isEqualTo("-5.0")
    }

    @Test
    fun `PriceRangeRule passes boundary values`() {
        val rule = PriceRangeRule()

        val minItem = ValidatableItem.fromCatalogItem("c1", "b1", price = 0.01, name = "Min")
        val maxItem = ValidatableItem.fromCatalogItem("c2", "b1", price = 9999.99, name = "Max")

        assertThat(rule.validate(minItem)).isEmpty()
        assertThat(rule.validate(maxItem)).isEmpty()
    }

    @Test
    fun `PriceRangeRule ignores non-catalog items`() {
        val rule = PriceRangeRule()
        val item = ValidatableItem.fromArticle(
            id = "a1", batchVersionId = "b1",
            publishedAt = now, title = "Article", externalUrl = "https://x.com"
        )

        assertThat(rule.validate(item)).isEmpty()
    }

    // InventoryNonNegativeRule tests

    @Test
    fun `InventoryNonNegativeRule passes zero quantity`() {
        val rule = InventoryNonNegativeRule()
        val item = ValidatableItem.fromInventorySnapshot(
            id = "s1", batchVersionId = "b1", quantity = 0, catalogItemId = "c1"
        )

        assertThat(rule.validate(item)).isEmpty()
    }

    @Test
    fun `InventoryNonNegativeRule passes positive quantity`() {
        val rule = InventoryNonNegativeRule()
        val item = ValidatableItem.fromInventorySnapshot(
            id = "s1", batchVersionId = "b1", quantity = 100, catalogItemId = "c1"
        )

        assertThat(rule.validate(item)).isEmpty()
    }

    @Test
    fun `InventoryNonNegativeRule fails negative quantity`() {
        val rule = InventoryNonNegativeRule()
        val item = ValidatableItem.fromInventorySnapshot(
            id = "s1", batchVersionId = "b1", quantity = -1, catalogItemId = "c1"
        )

        val errors = rule.validate(item)

        assertThat(errors).hasSize(1)
        assertThat(errors[0].message).contains("negative")
    }

    @Test
    fun `InventoryNonNegativeRule ignores non-inventory items`() {
        val rule = InventoryNonNegativeRule()
        val item = ValidatableItem.fromArticle(
            id = "a1", batchVersionId = "b1",
            publishedAt = now, title = "Article", externalUrl = "https://x.com"
        )

        assertThat(rule.validate(item)).isEmpty()
    }
}
