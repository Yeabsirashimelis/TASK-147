package com.eaglepoint.storefront.notification

import com.eaglepoint.storefront.data.repository.NotificationRepository
import com.eaglepoint.storefront.domain.model.Notification
import com.eaglepoint.storefront.domain.model.NotificationStatus
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class NotificationDispatcher(
    private val notificationRepository: NotificationRepository,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    suspend fun deliverPending() = withContext(dispatcher) {
        val pending = notificationRepository.getPendingDeliveries()
        for (notification in pending) {
            deliver(notification)
        }
    }

    suspend fun deliver(notification: Notification) = withContext(dispatcher) {
        try {
            // In-app delivery: mark as delivered (no external transport)
            notificationRepository.updateDeliveryStatus(
                id = notification.id,
                status = NotificationStatus.DELIVERED,
                retryCount = notification.retryCount,
                deliveredAt = System.currentTimeMillis(),
                failureReason = null
            )
        } catch (e: Exception) {
            val newRetryCount = notification.retryCount + 1
            val newStatus = if (newRetryCount >= notification.maxRetries) {
                NotificationStatus.EXHAUSTED
            } else {
                NotificationStatus.FAILED
            }

            notificationRepository.updateDeliveryStatus(
                id = notification.id,
                status = newStatus,
                retryCount = newRetryCount,
                deliveredAt = null,
                failureReason = e.message
            )
        }
    }

    suspend fun retryFailed() = withContext(dispatcher) {
        val retryable = notificationRepository.getPendingDeliveries()
            .filter { it.status == NotificationStatus.FAILED && it.retryCount < it.maxRetries }
        for (notification in retryable) {
            deliver(notification)
        }
    }
}
