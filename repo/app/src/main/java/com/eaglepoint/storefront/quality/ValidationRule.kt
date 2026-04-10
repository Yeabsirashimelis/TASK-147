package com.eaglepoint.storefront.quality

import com.eaglepoint.storefront.domain.model.ValidationError
import com.eaglepoint.storefront.domain.model.ValidationSeverity
import java.util.UUID

sealed class ValidationRule(
    val name: String,
    val description: String,
    val severity: ValidationSeverity = ValidationSeverity.ERROR
) {

    abstract fun validate(item: ValidatableItem): List<ValidationError>

    protected fun createError(
        batchVersionId: String,
        entityType: String,
        entityId: String,
        fieldName: String,
        message: String,
        actualValue: String? = null
    ): ValidationError {
        return ValidationError(
            id = UUID.randomUUID().toString(),
            batchVersionId = batchVersionId,
            entityType = entityType,
            entityId = entityId,
            fieldName = fieldName,
            ruleName = name,
            message = message,
            actualValue = actualValue,
            severity = severity,
            createdAt = System.currentTimeMillis()
        )
    }
}

class PublishTimeRule(
    private val maxAgeDays: Int = MAX_AGE_DAYS
) : ValidationRule(
    name = "publish_time_range",
    description = "Publish time must be within the last $MAX_AGE_DAYS days"
) {

    override fun validate(item: ValidatableItem): List<ValidationError> {
        if (item.entityType != "article") return emptyList()

        val publishedAt = item.fields["publishedAt"] as? Long ?: return emptyList()
        val now = System.currentTimeMillis()
        val cutoff = now - (maxAgeDays.toLong() * 24 * 60 * 60 * 1000)

        val errors = mutableListOf<ValidationError>()

        if (publishedAt < cutoff) {
            errors.add(
                createError(
                    batchVersionId = item.batchVersionId,
                    entityType = item.entityType,
                    entityId = item.entityId,
                    fieldName = "publishedAt",
                    message = "Publish time is older than $maxAgeDays days",
                    actualValue = publishedAt.toString()
                )
            )
        }

        if (publishedAt > now + FUTURE_TOLERANCE_MS) {
            errors.add(
                createError(
                    batchVersionId = item.batchVersionId,
                    entityType = item.entityType,
                    entityId = item.entityId,
                    fieldName = "publishedAt",
                    message = "Publish time is in the future",
                    actualValue = publishedAt.toString()
                )
            )
        }

        return errors
    }

    companion object {
        const val MAX_AGE_DAYS = 365
        const val FUTURE_TOLERANCE_MS = 24 * 60 * 60 * 1000L // 1 day tolerance for timezone issues
    }
}

class PriceRangeRule(
    private val minPrice: Double = MIN_PRICE,
    private val maxPrice: Double = MAX_PRICE
) : ValidationRule(
    name = "price_range",
    description = "Price must be between $$MIN_PRICE and $$MAX_PRICE"
) {

    override fun validate(item: ValidatableItem): List<ValidationError> {
        if (item.entityType != "catalog_item") return emptyList()

        val price = item.fields["price"] as? Double ?: return emptyList()

        if (price < minPrice || price > maxPrice) {
            return listOf(
                createError(
                    batchVersionId = item.batchVersionId,
                    entityType = item.entityType,
                    entityId = item.entityId,
                    fieldName = "price",
                    message = "Price must be between $$minPrice and $$maxPrice",
                    actualValue = price.toString()
                )
            )
        }

        return emptyList()
    }

    companion object {
        const val MIN_PRICE = 0.01
        const val MAX_PRICE = 9999.99
    }
}

class InventoryNonNegativeRule : ValidationRule(
    name = "inventory_non_negative",
    description = "Inventory quantity cannot be negative"
) {

    override fun validate(item: ValidatableItem): List<ValidationError> {
        if (item.entityType != "inventory_snapshot") return emptyList()

        val quantity = item.fields["quantity"] as? Int ?: return emptyList()

        if (quantity < 0) {
            return listOf(
                createError(
                    batchVersionId = item.batchVersionId,
                    entityType = item.entityType,
                    entityId = item.entityId,
                    fieldName = "quantity",
                    message = "Inventory quantity cannot be negative",
                    actualValue = quantity.toString()
                )
            )
        }

        return emptyList()
    }
}
