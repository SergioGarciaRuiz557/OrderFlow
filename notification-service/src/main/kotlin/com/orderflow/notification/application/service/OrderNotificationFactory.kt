package com.orderflow.notification.application.service

import com.orderflow.notification.domain.model.Notification
import com.orderflow.notification.domain.model.NotificationType
import com.orderflow.notification.domain.model.OrderId
import com.orderflow.notification.domain.model.Recipient
import org.springframework.stereotype.Component

/**
 * Creates complete order notifications and their deterministic customer-facing content.
 *
 * Centralizing message construction prevents both application services from embedding text and
 * gives a future template-backed implementation one clear replacement point. This class is kept
 * intentionally small: localization, template engines, and remote content providers are not current
 * requirements.
 *
 * [Component] registers the factory in Spring's application context so it can be constructor-
 * injected into both notification services. Its public API itself remains independent of Spring.
 */
@Component
class OrderNotificationFactory {
    /**
     * Builds the message sent after an order is confirmed.
     *
     * @param orderId confirmed order included in the customer-facing text.
     * @param recipient destination that will receive the notification.
     * @return immutable confirmation notification ready for the output port.
     */
    fun confirmed(orderId: OrderId, recipient: Recipient): Notification = Notification(
        // Preserve the validated business identifier received at the input boundary.
        orderId = orderId,
        // Preserve the validated recipient; delivery adapters do not reinterpret application data.
        recipient = recipient,
        // The explicit type lets outbound adapters distinguish this message without parsing text.
        type = NotificationType.ORDER_CONFIRMED,
        // Interpolation produces stable content and avoids introducing a template engine prematurely.
        message = "Your order ${orderId.value} has been confirmed.",
    )

    /**
     * Builds the message sent after an order is cancelled.
     *
     * @param orderId cancelled order included in the customer-facing text.
     * @param recipient destination that will receive the notification.
     * @return immutable cancellation notification ready for the output port.
     */
    fun cancelled(orderId: OrderId, recipient: Recipient): Notification = Notification(
        // The same order identifier is carried as structured data and rendered in the message.
        orderId = orderId,
        // Recipient remains provider-neutral until the outbound adapter receives it.
        recipient = recipient,
        // Cancellation is modeled independently from confirmation in the supported type set.
        type = NotificationType.ORDER_CANCELLED,
        // Deterministic wording makes current behavior predictable and straightforward to test.
        message = "Your order ${orderId.value} has been cancelled.",
    )
}
