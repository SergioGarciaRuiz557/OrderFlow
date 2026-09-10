package com.orderflow.notification.application.service

import com.orderflow.notification.application.port.`in`.SendOrderCancelledNotificationCommand
import com.orderflow.notification.application.port.`in`.SendOrderConfirmedNotificationCommand
import com.orderflow.notification.application.port.`out`.NotificationDeliveryException
import com.orderflow.notification.application.port.`out`.NotificationSender
import com.orderflow.notification.domain.model.Notification
import com.orderflow.notification.domain.model.NotificationType
import com.orderflow.notification.domain.model.OrderId
import com.orderflow.notification.domain.model.Recipient
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class NotificationServicesTest {
    private val notificationSender = mockk<NotificationSender>()
    private val notificationFactory = OrderNotificationFactory()

    @Test
    fun `should send confirmation notification`() {
        every { notificationSender.send(any()) } returns Unit
        val notification = slot<Notification>()
        val service = SendOrderConfirmedNotificationService(notificationFactory, notificationSender)

        service.send(
            SendOrderConfirmedNotificationCommand(
                orderId = OrderId("order-123"),
                recipient = Recipient("customer@example.com"),
            ),
        )

        verify(exactly = 1) { notificationSender.send(capture(notification)) }
        assertEquals(NotificationType.ORDER_CONFIRMED, notification.captured.type)
        assertEquals(OrderId("order-123"), notification.captured.orderId)
        assertEquals("Your order order-123 has been confirmed.", notification.captured.message)
    }

    @Test
    fun `should send cancellation notification`() {
        every { notificationSender.send(any()) } returns Unit
        val notification = slot<Notification>()
        val service = SendOrderCancelledNotificationService(notificationFactory, notificationSender)

        service.send(
            SendOrderCancelledNotificationCommand(
                orderId = OrderId("order-456"),
                recipient = Recipient("customer@example.com"),
            ),
        )

        verify(exactly = 1) { notificationSender.send(capture(notification)) }
        assertEquals(NotificationType.ORDER_CANCELLED, notification.captured.type)
        assertEquals(OrderId("order-456"), notification.captured.orderId)
        assertEquals("Your order order-456 has been cancelled.", notification.captured.message)
    }

    @Test
    fun `should invoke notification sender with expected recipient`() {
        every { notificationSender.send(any()) } returns Unit
        val expectedRecipient = Recipient("expected@example.com")
        val service = SendOrderConfirmedNotificationService(notificationFactory, notificationSender)

        service.send(SendOrderConfirmedNotificationCommand(OrderId("order-789"), expectedRecipient))

        verify(exactly = 1) {
            notificationSender.send(match { it.recipient == expectedRecipient })
        }
    }

    @Test
    fun `should propagate sender technical failure`() {
        val failure = NotificationDeliveryException("provider unavailable")
        every { notificationSender.send(any()) } throws failure
        val service = SendOrderCancelledNotificationService(notificationFactory, notificationSender)

        val thrown = assertThrows(NotificationDeliveryException::class.java) {
            service.send(
                SendOrderCancelledNotificationCommand(
                    OrderId("order-technical-failure"),
                    Recipient("customer@example.com"),
                ),
            )
        }

        assertSame(failure, thrown)
        verify(exactly = 1) { notificationSender.send(any()) }
    }
}
