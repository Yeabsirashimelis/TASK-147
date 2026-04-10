package com.eaglepoint.storefront.domain.usecase

import com.eaglepoint.storefront.data.repository.NotificationRepository
import com.eaglepoint.storefront.domain.model.Notification
import com.eaglepoint.storefront.domain.model.NotificationEventType
import com.eaglepoint.storefront.domain.model.NotificationStatus
import com.eaglepoint.storefront.notification.NotificationDispatcher
import com.eaglepoint.storefront.notification.TemplateRenderer
import java.util.UUID

class SendNotificationUseCase(
    private val notificationRepository: NotificationRepository,
    private val templateRenderer: TemplateRenderer,
    private val notificationDispatcher: NotificationDispatcher
) {

    suspend operator fun invoke(
        recipientId: String,
        eventType: NotificationEventType,
        variables: Map<String, String> = emptyMap(),
        maxRetries: Int = DEFAULT_MAX_RETRIES
    ): Result<Notification> {
        return try {
            val template = notificationRepository.findTemplateByEventType(eventType)

            val title: String
            val content: String
            val templateId: String?

            if (template != null) {
                val rendered = templateRenderer.renderTemplate(template, variables)
                title = rendered.title
                content = rendered.body
                templateId = template.id
            } else {
                title = formatDefaultTitle(eventType)
                content = formatDefaultContent(eventType, variables)
                templateId = null
            }

            val now = System.currentTimeMillis()
            val notification = Notification(
                id = UUID.randomUUID().toString(),
                recipientId = recipientId,
                templateId = templateId,
                eventType = eventType,
                title = title,
                content = content,
                status = NotificationStatus.PENDING,
                maxRetries = maxRetries,
                createdAt = now,
                updatedAt = now
            )

            notificationRepository.insert(notification)
            notificationDispatcher.deliver(notification)

            Result.success(notification)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deliverPending() {
        notificationDispatcher.deliverPending()
    }

    suspend fun retryFailed() {
        notificationDispatcher.retryFailed()
    }

    private fun formatDefaultTitle(eventType: NotificationEventType): String {
        return when (eventType) {
            NotificationEventType.INGESTION_FAILURE -> "Ingestion Failure"
            NotificationEventType.BATCH_QUALITY_ALERT -> "Quality Alert"
            NotificationEventType.ORDER_CONFIRMATION -> "Order Confirmed"
            NotificationEventType.PRICE_LOCK_EXPIRY -> "Price Lock Expiring"
            NotificationEventType.ADMIN_REVIEW -> "Review Required"
            NotificationEventType.GENERAL -> "Notification"
        }
    }

    private fun formatDefaultContent(eventType: NotificationEventType, variables: Map<String, String>): String {
        val details = variables.entries.joinToString(", ") { "${it.key}: ${it.value}" }
        return when (eventType) {
            NotificationEventType.ORDER_CONFIRMATION ->
                "Your order ${variables["orderId"] ?: ""} has been confirmed. Total: ${variables["total"] ?: ""}."
            NotificationEventType.INGESTION_FAILURE ->
                "Ingestion failed for source: ${variables["sourceName"] ?: "unknown"}. ${variables["reason"] ?: ""}"
            NotificationEventType.BATCH_QUALITY_ALERT ->
                "Batch quality check failed. Error rate: ${variables["errorRate"] ?: "unknown"}."
            NotificationEventType.PRICE_LOCK_EXPIRY ->
                "Your price lock is expiring soon. Please complete checkout."
            NotificationEventType.ADMIN_REVIEW ->
                "Admin review required: ${variables["reason"] ?: details}"
            NotificationEventType.GENERAL -> details.ifBlank { "You have a new notification." }
        }
    }

    companion object {
        const val DEFAULT_MAX_RETRIES = 3
    }
}
