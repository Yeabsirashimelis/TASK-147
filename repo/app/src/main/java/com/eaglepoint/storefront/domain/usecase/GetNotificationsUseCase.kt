package com.eaglepoint.storefront.domain.usecase

import com.eaglepoint.storefront.data.repository.NotificationRepository
import com.eaglepoint.storefront.domain.model.Notification
import kotlinx.coroutines.flow.Flow

class GetNotificationsUseCase(
    private val notificationRepository: NotificationRepository
) {

    fun getAll(recipientId: String, limit: Int = 50, offset: Int = 0): Flow<List<Notification>> {
        return notificationRepository.getByRecipient(recipientId, limit, offset)
    }

    fun getUnread(recipientId: String): Flow<List<Notification>> {
        return notificationRepository.getUnreadByRecipient(recipientId)
    }

    fun getUnreadCount(recipientId: String): Flow<Int> {
        return notificationRepository.getUnreadCount(recipientId)
    }

    suspend fun markRead(notificationId: String) {
        notificationRepository.markRead(notificationId)
    }

    suspend fun markAllRead(recipientId: String) {
        notificationRepository.markAllRead(recipientId)
    }
}
