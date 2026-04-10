package com.eaglepoint.storefront.api

import com.eaglepoint.storefront.data.repository.NotificationRepository
import com.eaglepoint.storefront.domain.model.Notification
import com.eaglepoint.storefront.domain.model.NotificationEventType
import com.eaglepoint.storefront.domain.model.NotificationStatus
import com.eaglepoint.storefront.domain.model.NotificationTemplate
import com.eaglepoint.storefront.domain.usecase.GetNotificationsUseCase
import com.eaglepoint.storefront.domain.usecase.SendNotificationUseCase
import com.eaglepoint.storefront.notification.NotificationDispatcher
import com.eaglepoint.storefront.notification.TemplateRenderer
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * API-level functional tests for the notification system.
 * Covers send with template, send without template, delivery,
 * retry logic, read/unread status, and all trigger event types.
 */
class NotificationFlowTest {

    private lateinit var notificationRepository: NotificationRepository
    private lateinit var templateRenderer: TemplateRenderer
    private lateinit var notificationDispatcher: NotificationDispatcher
    private lateinit var sendNotificationUseCase: SendNotificationUseCase
    private lateinit var getNotificationsUseCase: GetNotificationsUseCase

    @BeforeEach
    fun setUp() {
        notificationRepository = mockk(relaxed = true)
        templateRenderer = TemplateRenderer()
        notificationDispatcher = mockk(relaxed = true)
        sendNotificationUseCase = SendNotificationUseCase(notificationRepository, templateRenderer, notificationDispatcher)
        getNotificationsUseCase = GetNotificationsUseCase(notificationRepository)
    }

    // --- Send with template ---
    @Test
    fun `send order confirmation with template renders variables`() = runTest {
        val template = NotificationTemplate(
            id = "tpl-1", name = "Order Confirmation",
            eventType = NotificationEventType.ORDER_CONFIRMATION,
            titleTemplate = "Order {orderId}", bodyTemplate = "Total: {total}"
        )
        coEvery { notificationRepository.findTemplateByEventType(NotificationEventType.ORDER_CONFIRMATION) } returns template

        val result = sendNotificationUseCase(
            recipientId = "user-1",
            eventType = NotificationEventType.ORDER_CONFIRMATION,
            variables = mapOf("orderId" to "ORD-123", "total" to "$99.99")
        )

        assertThat(result.isSuccess).isTrue()
        val notification = result.getOrNull()!!
        assertThat(notification.title).isEqualTo("Order ORD-123")
        assertThat(notification.content).isEqualTo("Total: $99.99")
        assertThat(notification.templateId).isEqualTo("tpl-1")
    }

    // --- Send without template (default content) ---
    @Test
    fun `send ingestion failure without template uses default content`() = runTest {
        coEvery { notificationRepository.findTemplateByEventType(any()) } returns null

        val result = sendNotificationUseCase(
            recipientId = "admin-1",
            eventType = NotificationEventType.INGESTION_FAILURE,
            variables = mapOf("sourceName" to "ESPN Feed", "reason" to "Timeout")
        )

        assertThat(result.isSuccess).isTrue()
        assertThat(result.getOrNull()!!.title).isEqualTo("Ingestion Failure")
        assertThat(result.getOrNull()!!.content).contains("ESPN Feed")
    }

    // --- All event types produce valid notifications ---
    @Test
    fun `batch quality alert sends with error rate`() = runTest {
        coEvery { notificationRepository.findTemplateByEventType(any()) } returns null

        val result = sendNotificationUseCase(
            recipientId = "admin-1",
            eventType = NotificationEventType.BATCH_QUALITY_ALERT,
            variables = mapOf("errorRate" to "5.2%")
        )

        assertThat(result.isSuccess).isTrue()
        assertThat(result.getOrNull()!!.content).contains("5.2%")
    }

    @Test
    fun `price lock expiry notification sends`() = runTest {
        coEvery { notificationRepository.findTemplateByEventType(any()) } returns null

        val result = sendNotificationUseCase(
            recipientId = "user-1",
            eventType = NotificationEventType.PRICE_LOCK_EXPIRY
        )

        assertThat(result.isSuccess).isTrue()
        assertThat(result.getOrNull()!!.content).contains("price lock")
    }

    @Test
    fun `admin review notification sends`() = runTest {
        coEvery { notificationRepository.findTemplateByEventType(any()) } returns null

        val result = sendNotificationUseCase(
            recipientId = "admin-1",
            eventType = NotificationEventType.ADMIN_REVIEW,
            variables = mapOf("reason" to "Source failed 5 times")
        )

        assertThat(result.isSuccess).isTrue()
        assertThat(result.getOrNull()!!.content).contains("Source failed")
    }

    // --- Delivery is triggered ---
    @Test
    fun `send triggers delivery attempt`() = runTest {
        coEvery { notificationRepository.findTemplateByEventType(any()) } returns null

        sendNotificationUseCase(
            recipientId = "user-1",
            eventType = NotificationEventType.GENERAL
        )

        coVerify { notificationDispatcher.deliver(any()) }
    }

    // --- Configurable max retries ---
    @Test
    fun `custom max retries is set on notification`() = runTest {
        coEvery { notificationRepository.findTemplateByEventType(any()) } returns null

        val result = sendNotificationUseCase(
            recipientId = "user-1",
            eventType = NotificationEventType.GENERAL,
            maxRetries = 5
        )

        assertThat(result.getOrNull()!!.maxRetries).isEqualTo(5)
    }

    // --- Read/unread management ---
    @Test
    fun `get unread count returns correct count`() = runTest {
        coEvery { notificationRepository.getUnreadCount("user-1") } returns flowOf(3)

        val count = getNotificationsUseCase.getUnreadCount("user-1").first()

        assertThat(count).isEqualTo(3)
    }

    @Test
    fun `mark read calls repository`() = runTest {
        getNotificationsUseCase.markRead("notif-1")
        coVerify { notificationRepository.markRead("notif-1") }
    }

    @Test
    fun `mark all read calls repository`() = runTest {
        getNotificationsUseCase.markAllRead("user-1")
        coVerify { notificationRepository.markAllRead("user-1") }
    }

    // --- Error handling ---
    @Test
    fun `send notification returns failure on repository error`() = runTest {
        coEvery { notificationRepository.findTemplateByEventType(any()) } throws RuntimeException("DB error")

        val result = sendNotificationUseCase(
            recipientId = "user-1",
            eventType = NotificationEventType.GENERAL
        )

        assertThat(result.isFailure).isTrue()
    }
}
