package com.eaglepoint.storefront.notification

import com.eaglepoint.storefront.data.repository.NotificationRepository
import com.eaglepoint.storefront.domain.model.Notification
import com.eaglepoint.storefront.domain.model.NotificationEventType
import com.eaglepoint.storefront.domain.model.NotificationStatus
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class NotificationDispatcherTest {

    private lateinit var notificationRepository: NotificationRepository
    private lateinit var dispatcher: NotificationDispatcher
    private val testDispatcher = StandardTestDispatcher()
    private val now = System.currentTimeMillis()

    private fun testNotification(
        id: String = "notif-1",
        status: NotificationStatus = NotificationStatus.PENDING,
        retryCount: Int = 0,
        maxRetries: Int = 3
    ) = Notification(
        id = id,
        recipientId = "user-1",
        eventType = NotificationEventType.ORDER_CONFIRMATION,
        title = "Test",
        content = "Test content",
        status = status,
        retryCount = retryCount,
        maxRetries = maxRetries,
        createdAt = now,
        updatedAt = now
    )

    @BeforeEach
    fun setUp() {
        notificationRepository = mockk(relaxed = true)
        dispatcher = NotificationDispatcher(notificationRepository, testDispatcher)
    }

    @Test
    fun `deliver marks notification as DELIVERED`() = runTest(testDispatcher) {
        val notification = testNotification()

        dispatcher.deliver(notification)

        coVerify {
            notificationRepository.updateDeliveryStatus(
                id = "notif-1",
                status = NotificationStatus.DELIVERED,
                retryCount = 0,
                deliveredAt = any(),
                failureReason = null
            )
        }
    }

    @Test
    fun `deliver increments retry count on failure`() = runTest(testDispatcher) {
        val notification = testNotification(retryCount = 1)
        coEvery {
            notificationRepository.updateDeliveryStatus(
                id = "notif-1",
                status = NotificationStatus.DELIVERED,
                retryCount = any(),
                deliveredAt = any(),
                failureReason = null
            )
        } throws RuntimeException("DB error")

        dispatcher.deliver(notification)

        coVerify {
            notificationRepository.updateDeliveryStatus(
                id = "notif-1",
                status = NotificationStatus.FAILED,
                retryCount = 2,
                deliveredAt = null,
                failureReason = "DB error"
            )
        }
    }

    @Test
    fun `deliver marks EXHAUSTED when max retries reached`() = runTest(testDispatcher) {
        val notification = testNotification(retryCount = 2, maxRetries = 3)
        coEvery {
            notificationRepository.updateDeliveryStatus(
                id = "notif-1",
                status = NotificationStatus.DELIVERED,
                retryCount = any(),
                deliveredAt = any(),
                failureReason = null
            )
        } throws RuntimeException("Still failing")

        dispatcher.deliver(notification)

        coVerify {
            notificationRepository.updateDeliveryStatus(
                id = "notif-1",
                status = NotificationStatus.EXHAUSTED,
                retryCount = 3,
                deliveredAt = null,
                failureReason = "Still failing"
            )
        }
    }

    @Test
    fun `deliverPending processes all pending notifications`() = runTest(testDispatcher) {
        val pending = listOf(
            testNotification("n1"),
            testNotification("n2")
        )
        coEvery { notificationRepository.getPendingDeliveries() } returns pending

        dispatcher.deliverPending()

        coVerify(exactly = 2) {
            notificationRepository.updateDeliveryStatus(
                id = any(),
                status = NotificationStatus.DELIVERED,
                retryCount = any(),
                deliveredAt = any(),
                failureReason = null
            )
        }
    }
}
