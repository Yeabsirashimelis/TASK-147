package com.eaglepoint.storefront.domain.model

data class Notification(
    val id: String,
    val recipientId: String,
    val templateId: String? = null,
    val eventType: NotificationEventType,
    val title: String,
    val content: String,
    val status: NotificationStatus = NotificationStatus.PENDING,
    val retryCount: Int = 0,
    val maxRetries: Int = 3,
    val failureReason: String? = null,
    val deliveredAt: Long? = null,
    val isRead: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long
)

enum class NotificationStatus {
    PENDING,
    DELIVERED,
    FAILED,
    EXHAUSTED
}

enum class NotificationEventType {
    INGESTION_FAILURE,
    BATCH_QUALITY_ALERT,
    ORDER_CONFIRMATION,
    PRICE_LOCK_EXPIRY,
    ADMIN_REVIEW,
    GENERAL
}

data class NotificationTemplate(
    val id: String,
    val name: String,
    val eventType: NotificationEventType,
    val titleTemplate: String,
    val bodyTemplate: String,
    val isActive: Boolean = true
)
