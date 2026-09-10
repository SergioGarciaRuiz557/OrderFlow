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

/**
 * Unit tests for confirmation and cancellation application-service orchestration.
 *
 * These tests do not load Spring. The real [OrderNotificationFactory] verifies current deterministic
 * content construction, while a MockK [NotificationSender] isolates external delivery. This keeps
 * the scenarios focused on the application contract: build the right notification, call the output
 * port once, preserve the recipient, and propagate technical failures.
 */
class NotificationServicesTest {
    /** Mock output port used to observe delivery attempts without producing log or network effects. */
    private val notificationSender = mockk<NotificationSender>()

    /** Real stateless factory used so tests cover the exact production message content. */
    private val notificationFactory = OrderNotificationFactory()

    /**
     * Verifies the complete successful confirmation flow and its generated model.
     *
     * The assertion set protects the semantic type, order correlation, and exact deterministic text,
     * while MockK verifies that delivery is requested exactly once.
     */
    @Test
    fun `should send confirmation notification`() {
        // Arrange: configure the mocked sender to represent a successful Unit-returning delivery.
        every { notificationSender.send(any()) } returns Unit

        // Arrange: the slot captures the actual notification passed through the output port.
        val notification = slot<Notification>()

        // Arrange: instantiate the application service directly with production and test dependencies.
        val service = SendOrderConfirmedNotificationService(notificationFactory, notificationSender)

        // Act: execute the public input-port operation with validated command values.
        service.send(
            SendOrderConfirmedNotificationCommand(
                // The order id must be preserved structurally and interpolated into the message.
                orderId = OrderId("order-123"),
                // A valid email recipient satisfies the lightweight domain validation.
                recipient = Recipient("customer@example.com"),
            ),
        )

        // Assert: one delivery occurred, and capture its argument for field-level verification.
        verify(exactly = 1) { notificationSender.send(capture(notification)) }

        // Assert: the factory classified the message as an order confirmation.
        assertEquals(NotificationType.ORDER_CONFIRMED, notification.captured.type)

        // Assert: the outbound model retains the input command's order identifier.
        assertEquals(OrderId("order-123"), notification.captured.orderId)

        // Assert: message wording is deterministic and contains the correct order identifier.
        assertEquals("Your order order-123 has been confirmed.", notification.captured.message)
    }

    /**
     * Verifies the successful cancellation flow produces cancellation-specific type and content.
     *
     * This separate scenario prevents confirmation wording or classification from being reused
     * accidentally for an `OrderCancelledEvent`.
     */
    @Test
    fun `should send cancellation notification`() {
        // Arrange: a normal return from the mocked sender represents successful delivery.
        every { notificationSender.send(any()) } returns Unit

        // Arrange: capture the exact model submitted by the cancellation service.
        val notification = slot<Notification>()

        // Arrange: assemble the unit under test without starting the Spring container.
        val service = SendOrderCancelledNotificationService(notificationFactory, notificationSender)

        // Act: submit a cancellation command through the service's input-port implementation.
        service.send(
            SendOrderCancelledNotificationCommand(
                orderId = OrderId("order-456"),
                recipient = Recipient("customer@example.com"),
            ),
        )

        // Assert: the output port is called once and its argument is available in the slot.
        verify(exactly = 1) { notificationSender.send(capture(notification)) }

        // Assert: cancellation remains semantically distinct from confirmation.
        assertEquals(NotificationType.ORDER_CANCELLED, notification.captured.type)

        // Assert: the application preserves the cancelled order's identifier.
        assertEquals(OrderId("order-456"), notification.captured.orderId)

        // Assert: deterministic cancellation text contains the correct identifier and state.
        assertEquals("Your order order-456 has been cancelled.", notification.captured.message)
    }

    /**
     * Verifies that the intended customer is preserved across command-to-notification mapping.
     *
     * Recipient correctness is tested explicitly because delivering valid content to the wrong
     * address would be a critical application failure even when all other fields are correct.
     */
    @Test
    fun `should invoke notification sender with expected recipient`() {
        // Arrange: allow any notification to be sent successfully by the mock.
        every { notificationSender.send(any()) } returns Unit

        // Arrange: keep one typed recipient instance as the expected destination.
        val expectedRecipient = Recipient("expected@example.com")

        // Arrange: use the confirmation workflow; recipient mapping is shared by both flows.
        val service = SendOrderConfirmedNotificationService(notificationFactory, notificationSender)

        // Act: request delivery to the expected address.
        service.send(SendOrderConfirmedNotificationCommand(OrderId("order-789"), expectedRecipient))

        // Assert: exactly one outbound call contains that same typed recipient value.
        verify(exactly = 1) {
            notificationSender.send(match { it.recipient == expectedRecipient })
        }
    }

    /**
     * Verifies that a sender outage remains a notification-specific technical failure.
     *
     * The application must not swallow the exception or turn it into an order-domain result. Exact
     * instance comparison proves that the original failure is propagated unchanged, preserving its
     * message, cause, and future diagnostic context for an asynchronous retry mechanism.
     */
    @Test
    fun `should propagate sender technical failure`() {
        // Arrange: create the precise failure that the mocked outbound adapter will report.
        val failure = NotificationDeliveryException("provider unavailable")

        // Arrange: configure every delivery attempt to throw rather than return successfully.
        every { notificationSender.send(any()) } throws failure

        // Arrange: cancellation is sufficient to verify the shared propagation policy.
        val service = SendOrderCancelledNotificationService(notificationFactory, notificationSender)

        // Act and assert: execute the use case and capture its expected technical exception.
        val thrown = assertThrows(NotificationDeliveryException::class.java) {
            service.send(
                SendOrderCancelledNotificationCommand(
                    // A valid order ensures the scenario reaches the sender rather than failing validation.
                    OrderId("order-technical-failure"),
                    // A valid recipient likewise keeps the test focused on outbound failure behavior.
                    Recipient("customer@example.com"),
                ),
            )
        }

        // Assert: the application rethrows the original adapter failure without wrapping or replacing it.
        assertSame(failure, thrown)

        // Assert: exactly one delivery was attempted before the technical failure escaped.
        verify(exactly = 1) { notificationSender.send(any()) }
    }
}
