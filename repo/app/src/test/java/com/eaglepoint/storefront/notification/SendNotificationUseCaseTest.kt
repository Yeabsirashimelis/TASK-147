package com.eaglepoint.storefront.notification

import com.eaglepoint.storefront.data.repository.NotificationRepository
import com.eaglepoint.storefront.domain.model.NotificationEventType
import com.eaglepoint.storefront.domain.model.NotificationTemplate
import com.eaglepoint.storefront.domain.usecase.SendNotificationUseCase
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class SendNotificationUseCaseTest {

    private lateinit var notificationRepository: NotificationRepository
    private lateinit var templateRenderer: TemplateRenderer
    private lateinit var notificationDispatcher: NotificationDispatcher
    private lateinit var sendNotificationUseCase: SendNotificationUseCase

    @BeforeEach
    fun setUp() {
        notificationRepository = mockk(relaxed = true)
        templateRenderer = TemplateRenderer()
        notificationDispatcher = mockk(relaxed = true)
        sendNotificationUseCase = SendNotificationUseCase(
            notificationRepository, templateRenderer, notificationDispatcher
        )
    }

    @Test
    fun `sends notification with template`() = runTest {
        val template = NotificationTemplate(
            id = "tpl-order",
            name = "Order Confirmation",
            eventType = NotificationEventType.ORDER_CONFIRMATION,
            titleTemplate = "Order {orderId}",
            bodyTemplate = "Your order {orderId} for {total} is confirmed."
        )
        coEvery {
            notificationRepository.findTemplateByEventType(NotificationEventType.ORDER_CONFIRMATION)
        } returns template

        val result = sendNotificationUseCase(
            recipientId = "user-1",
            eventType = NotificationEventType.ORDER_CONFIRMATION,
            variables = mapOf("orderId" to "ORD-789", "total" to "$99.99")
        )

        assertThat(result.isSuccess).isTrue()
        val notification = result.getOrNull()!!
        assertThat(notification.title).isEqualTo("Order ORD-789")
        assertThat(notification.content).isEqualTo("Your order ORD-789 for $99.99 is confirmed.")
        assertThat(notification.templateId).isEqualTo("tpl-order")

        coVerify { notificationRepository.insert(any()) }
        coVerify { notificationDispatcher.deliver(any()) }
    }

    @Test
    fun `sends notification with default content when no template`() = runTest {
        coEvery {
            notificationRepository.findTemplateByEventType(NotificationEventType.INGESTION_FAILURE)
        } returns null

        val result = sendNotificationUseCase(
            recipientId = "admin-1",
            eventType = NotificationEventType.INGESTION_FAILURE,
            variables = mapOf("sourceName" to "ESPN Feed", "reason" to "Timeout")
        )

        assertThat(result.isSuccess).isTrue()
        val notification = result.getOrNull()!!
        assertThat(notification.title).isEqualTo("Ingestion Failure")
        assertThat(notification.content).contains("ESPN Feed")
        assertThat(notification.templateId).isNull()
    }

    @Test
    fun `sends price lock expiry notification`() = runTest {
        coEvery {
            notificationRepository.findTemplateByEventType(NotificationEventType.PRICE_LOCK_EXPIRY)
        } returns null

        val result = sendNotificationUseCase(
            recipientId = "user-1",
            eventType = NotificationEventType.PRICE_LOCK_EXPIRY
        )

        assertThat(result.isSuccess).isTrue()
        assertThat(result.getOrNull()!!.content).contains("price lock")
    }

    @Test
    fun `sends batch quality alert notification`() = runTest {
        coEvery {
            notificationRepository.findTemplateByEventType(NotificationEventType.BATCH_QUALITY_ALERT)
        } returns null

        val result = sendNotificationUseCase(
            recipientId = "admin-1",
            eventType = NotificationEventType.BATCH_QUALITY_ALERT,
            variables = mapOf("errorRate" to "5.2%")
        )

        assertThat(result.isSuccess).isTrue()
        assertThat(result.getOrNull()!!.content).contains("5.2%")
    }

    @Test
    fun `respects max retries setting`() = runTest {
        coEvery {
            notificationRepository.findTemplateByEventType(any())
        } returns null

        val result = sendNotificationUseCase(
            recipientId = "user-1",
            eventType = NotificationEventType.GENERAL,
            maxRetries = 5
        )

        assertThat(result.isSuccess).isTrue()
        assertThat(result.getOrNull()!!.maxRetries).isEqualTo(5)
    }

    @Test
    fun `returns failure on exception`() = runTest {
        coEvery { notificationRepository.findTemplateByEventType(any()) } throws RuntimeException("DB error")

        val result = sendNotificationUseCase(
            recipientId = "user-1",
            eventType = NotificationEventType.GENERAL
        )

        assertThat(result.isFailure).isTrue()
    }
}
