package com.eaglepoint.storefront.data.repository

import com.eaglepoint.storefront.data.db.dao.NotificationDao
import com.eaglepoint.storefront.data.db.dao.NotificationTemplateDao
import com.eaglepoint.storefront.data.db.entity.NotificationEntity
import com.eaglepoint.storefront.data.db.entity.NotificationTemplateEntity
import com.eaglepoint.storefront.domain.model.Notification
import com.eaglepoint.storefront.domain.model.NotificationEventType
import com.eaglepoint.storefront.domain.model.NotificationStatus
import com.eaglepoint.storefront.domain.model.NotificationTemplate
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class NotificationRepository(
    private val notificationDao: NotificationDao,
    private val templateDao: NotificationTemplateDao,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    suspend fun insert(notification: Notification) = withContext(dispatcher) {
        notificationDao.insert(domainToEntity(notification))
    }

    fun getByRecipient(recipientId: String, limit: Int = 50, offset: Int = 0): Flow<List<Notification>> {
        return notificationDao.getByRecipient(recipientId, limit, offset).map { it.map(::entityToDomain) }
    }

    fun getUnreadByRecipient(recipientId: String): Flow<List<Notification>> {
        return notificationDao.getUnreadByRecipient(recipientId).map { it.map(::entityToDomain) }
    }

    fun getUnreadCount(recipientId: String): Flow<Int> {
        return notificationDao.getUnreadCount(recipientId)
    }

    suspend fun markRead(id: String) = withContext(dispatcher) {
        notificationDao.markRead(id, System.currentTimeMillis())
    }

    suspend fun markAllRead(recipientId: String) = withContext(dispatcher) {
        notificationDao.markAllRead(recipientId, System.currentTimeMillis())
    }

    suspend fun getPendingDeliveries(): List<Notification> = withContext(dispatcher) {
        notificationDao.getPendingDeliveries().map(::entityToDomain)
    }

    suspend fun updateDeliveryStatus(
        id: String,
        status: NotificationStatus,
        retryCount: Int,
        deliveredAt: Long?,
        failureReason: String?
    ) = withContext(dispatcher) {
        notificationDao.updateDeliveryStatus(
            id, status.name, retryCount, failureReason, deliveredAt, System.currentTimeMillis()
        )
    }

    suspend fun findTemplateByEventType(eventType: NotificationEventType): NotificationTemplate? =
        withContext(dispatcher) {
            templateDao.findByEventType(eventType.name)?.let(::templateEntityToDomain)
        }

    fun getAllTemplates(): Flow<List<NotificationTemplate>> {
        return templateDao.getAll().map { it.map(::templateEntityToDomain) }
    }

    suspend fun saveTemplate(template: NotificationTemplate) = withContext(dispatcher) {
        templateDao.upsert(templateDomainToEntity(template))
    }

    private fun entityToDomain(entity: NotificationEntity): Notification {
        return Notification(
            id = entity.id,
            recipientId = entity.recipientId,
            templateId = entity.templateId,
            eventType = try { NotificationEventType.valueOf(entity.eventType) } catch (e: Exception) { NotificationEventType.GENERAL },
            title = entity.title,
            content = entity.content,
            status = try { NotificationStatus.valueOf(entity.status) } catch (e: Exception) { NotificationStatus.PENDING },
            retryCount = entity.retryCount,
            maxRetries = entity.maxRetries,
            failureReason = entity.failureReason,
            deliveredAt = entity.deliveredAt,
            isRead = entity.isRead,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt
        )
    }

    private fun domainToEntity(n: Notification): NotificationEntity {
        return NotificationEntity(
            id = n.id,
            recipientId = n.recipientId,
            templateId = n.templateId,
            eventType = n.eventType.name,
            title = n.title,
            content = n.content,
            status = n.status.name,
            retryCount = n.retryCount,
            maxRetries = n.maxRetries,
            failureReason = n.failureReason,
            deliveredAt = n.deliveredAt,
            isRead = n.isRead,
            createdAt = n.createdAt,
            updatedAt = n.updatedAt
        )
    }

    private fun templateEntityToDomain(e: NotificationTemplateEntity): NotificationTemplate {
        return NotificationTemplate(
            id = e.id,
            name = e.name,
            eventType = try { NotificationEventType.valueOf(e.eventType) } catch (ex: Exception) { NotificationEventType.GENERAL },
            titleTemplate = e.titleTemplate,
            bodyTemplate = e.bodyTemplate,
            isActive = e.isActive
        )
    }

    private fun templateDomainToEntity(t: NotificationTemplate): NotificationTemplateEntity {
        return NotificationTemplateEntity(
            id = t.id,
            name = t.name,
            eventType = t.eventType.name,
            titleTemplate = t.titleTemplate,
            bodyTemplate = t.bodyTemplate,
            isActive = t.isActive,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
    }
}
