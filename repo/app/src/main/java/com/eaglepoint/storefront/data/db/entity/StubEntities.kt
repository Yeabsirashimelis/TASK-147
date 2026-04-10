package com.eaglepoint.storefront.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "source_rules",
    indices = [Index(value = ["name"], unique = true)]
)
data class SourceRuleEntity(
    @PrimaryKey val id: String,
    val name: String,
    val url: String,
    @ColumnInfo(name = "feed_type") val feedType: String,
    @ColumnInfo(name = "parse_selector") val parseSelector: String? = null,
    @ColumnInfo(name = "allowed_domains") val allowedDomains: String? = null,
    @ColumnInfo(name = "blocked_domains") val blockedDomains: String? = null,
    @ColumnInfo(name = "allowed_keywords") val allowedKeywords: String? = null,
    @ColumnInfo(name = "blocked_keywords") val blockedKeywords: String? = null,
    @ColumnInfo(name = "interval_hours") val intervalHours: Int = 6,
    @ColumnInfo(name = "request_delay_ms") val requestDelayMs: Long = 2000,
    @ColumnInfo(name = "is_active") val isActive: Boolean = true,
    @ColumnInfo(name = "rule_version") val ruleVersion: Int = 1,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long
)

@Entity(
    tableName = "ingestion_job_runs",
    foreignKeys = [
        ForeignKey(
            entity = SourceRuleEntity::class,
            parentColumns = ["id"],
            childColumns = ["source_rule_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["source_rule_id"]),
        Index(value = ["started_at"]),
        Index(value = ["status"])
    ]
)
data class IngestionJobRunEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "source_rule_id") val sourceRuleId: String,
    @ColumnInfo(name = "rule_version") val ruleVersion: Int,
    val status: String,
    @ColumnInfo(name = "items_parsed") val itemsParsed: Int = 0,
    @ColumnInfo(name = "items_stored") val itemsStored: Int = 0,
    @ColumnInfo(name = "failure_reason") val failureReason: String? = null,
    @ColumnInfo(name = "attempt_number") val attemptNumber: Int = 1,
    @ColumnInfo(name = "batch_version_id") val batchVersionId: String? = null,
    @ColumnInfo(name = "started_at") val startedAt: Long,
    @ColumnInfo(name = "completed_at") val completedAt: Long? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long
)

@Entity(
    tableName = "articles",
    foreignKeys = [
        ForeignKey(
            entity = SourceRuleEntity::class,
            parentColumns = ["id"],
            childColumns = ["source_rule_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["source_rule_id", "published_at"]),
        Index(value = ["external_url"], unique = true),
        Index(value = ["published_at"]),
        Index(value = ["is_saved_offline"]),
        Index(value = ["team"]),
        Index(value = ["league"])
    ]
)
data class ArticleEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "source_rule_id") val sourceRuleId: String,
    @ColumnInfo(name = "batch_version_id") val batchVersionId: String? = null,
    val title: String,
    val summary: String? = null,
    val content: String? = null,
    val author: String? = null,
    @ColumnInfo(name = "external_url") val externalUrl: String,
    @ColumnInfo(name = "image_url") val imageUrl: String? = null,
    val team: String? = null,
    val league: String? = null,
    @ColumnInfo(name = "is_saved_offline") val isSavedOffline: Boolean = false,
    @ColumnInfo(name = "saved_at") val savedAt: Long? = null,
    @ColumnInfo(name = "curation_status") val curationStatus: String = "PENDING_REVIEW",
    @ColumnInfo(name = "curated_by") val curatedBy: String? = null,
    @ColumnInfo(name = "is_featured") val isFeatured: Boolean = false,
    val topic: String? = null,
    @ColumnInfo(name = "published_at") val publishedAt: Long? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long
)

@Entity(
    tableName = "ingestion_alerts",
    foreignKeys = [
        ForeignKey(
            entity = SourceRuleEntity::class,
            parentColumns = ["id"],
            childColumns = ["source_rule_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["source_rule_id"]),
        Index(value = ["is_acknowledged"])
    ]
)
data class IngestionAlertEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "source_rule_id") val sourceRuleId: String,
    @ColumnInfo(name = "source_name") val sourceName: String,
    @ColumnInfo(name = "failure_count") val failureCount: Int,
    val message: String,
    @ColumnInfo(name = "is_acknowledged") val isAcknowledged: Boolean = false,
    @ColumnInfo(name = "created_at") val createdAt: Long
)

@Entity(
    tableName = "catalog_items",
    indices = [Index(value = ["sku"], unique = true)]
)
data class CatalogItemEntity(
    @PrimaryKey val id: String,
    val sku: String,
    val name: String = "",
    val description: String? = null,
    val price: Double = 0.0,
    @ColumnInfo(name = "image_url") val imageUrl: String? = null,
    val category: String? = null,
    @ColumnInfo(name = "is_active") val isActive: Boolean = true,
    @ColumnInfo(name = "source_rule_id") val sourceRuleId: String? = null,
    @ColumnInfo(name = "batch_version_id") val batchVersionId: String? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long
)

@Entity(
    tableName = "inventory_snapshots",
    indices = [Index(value = ["catalog_item_id"])]
)
data class InventorySnapshotEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "catalog_item_id") val catalogItemId: String,
    val quantity: Int = 0,
    @ColumnInfo(name = "reserved_quantity") val reservedQuantity: Int = 0,
    @ColumnInfo(name = "batch_version_id") val batchVersionId: String? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long
)

@Entity(
    tableName = "carts",
    indices = [Index(value = ["user_id"])]
)
data class CartEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "user_id") val userId: String? = null,
    val status: String = "ACTIVE",
    @ColumnInfo(name = "coupon_id") val couponId: String? = null,
    @ColumnInfo(name = "price_locked_at") val priceLockedAt: Long? = null,
    @ColumnInfo(name = "price_lock_expires_at") val priceLockExpiresAt: Long? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long
)

@Entity(
    tableName = "cart_line_items",
    foreignKeys = [
        ForeignKey(
            entity = CartEntity::class,
            parentColumns = ["id"],
            childColumns = ["cart_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["cart_id", "sku"], unique = true),
        Index(value = ["cart_id"])
    ]
)
data class CartLineItemEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "cart_id") val cartId: String,
    @ColumnInfo(name = "catalog_item_id") val catalogItemId: String,
    val sku: String,
    val name: String,
    @ColumnInfo(name = "unit_price") val unitPrice: Double,
    val quantity: Int = 1,
    @ColumnInfo(name = "locked_price") val lockedPrice: Double? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long
)

@Entity(
    tableName = "price_rules",
    indices = [Index(value = ["is_active"])]
)
data class PriceRuleEntity(
    @PrimaryKey val id: String,
    val name: String,
    val type: String,
    @ColumnInfo(name = "min_order_amount") val minOrderAmount: Double? = null,
    @ColumnInfo(name = "discount_percent") val discountPercent: Double? = null,
    @ColumnInfo(name = "discount_amount") val discountAmount: Double? = null,
    @ColumnInfo(name = "is_active") val isActive: Boolean = true,
    val priority: Int = 0,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long
)

@Entity(
    tableName = "coupons",
    indices = [Index(value = ["code"], unique = true)]
)
data class CouponEntity(
    @PrimaryKey val id: String,
    val code: String,
    val description: String? = null,
    @ColumnInfo(name = "discount_percent") val discountPercent: Double? = null,
    @ColumnInfo(name = "discount_amount") val discountAmount: Double? = null,
    @ColumnInfo(name = "min_order_amount") val minOrderAmount: Double = 0.0,
    @ColumnInfo(name = "max_uses") val maxUses: Int? = null,
    @ColumnInfo(name = "current_uses") val currentUses: Int = 0,
    @ColumnInfo(name = "is_active") val isActive: Boolean = true,
    @ColumnInfo(name = "expires_at") val expiresAt: Long? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long
)

@Entity(
    tableName = "tax_rates",
    indices = [Index(value = ["state_code"], unique = true)]
)
data class TaxRateEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "state_code") val stateCode: String,
    @ColumnInfo(name = "state_name") val stateName: String,
    val rate: Double,
    @ColumnInfo(name = "is_active") val isActive: Boolean = true,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long
)

@Entity(
    tableName = "notifications",
    indices = [
        Index(value = ["recipient_id"]),
        Index(value = ["status"]),
        Index(value = ["event_type"]),
        Index(value = ["created_at"])
    ]
)
data class NotificationEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "recipient_id") val recipientId: String,
    @ColumnInfo(name = "template_id") val templateId: String? = null,
    @ColumnInfo(name = "event_type") val eventType: String,
    val title: String,
    val content: String,
    val status: String = "PENDING",
    @ColumnInfo(name = "retry_count") val retryCount: Int = 0,
    @ColumnInfo(name = "max_retries") val maxRetries: Int = 3,
    @ColumnInfo(name = "failure_reason") val failureReason: String? = null,
    @ColumnInfo(name = "delivered_at") val deliveredAt: Long? = null,
    @ColumnInfo(name = "is_read") val isRead: Boolean = false,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long
)

@Entity(
    tableName = "notification_templates",
    indices = [Index(value = ["event_type"], unique = true)]
)
data class NotificationTemplateEntity(
    @PrimaryKey val id: String,
    val name: String,
    @ColumnInfo(name = "event_type") val eventType: String,
    @ColumnInfo(name = "title_template") val titleTemplate: String,
    @ColumnInfo(name = "body_template") val bodyTemplate: String,
    @ColumnInfo(name = "is_active") val isActive: Boolean = true,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long
)
