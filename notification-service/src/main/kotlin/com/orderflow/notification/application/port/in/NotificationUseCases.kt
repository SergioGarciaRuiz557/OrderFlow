package com.orderflow.notification.application.port.`in`

import com.orderflow.notification.domain.model.OrderId
import com.orderflow.notification.domain.model.Recipient

/** Framework- and transport-independent input for an order confirmation notification. */
data class SendOrderConfirmedNotificationCommand(
    val orderId: OrderId,
    val recipient: Recipient,
)

/** Framework- and transport-independent input for an order cancellation notification. */
data class SendOrderCancelledNotificationCommand(
    val orderId: OrderId,
    val recipient: Recipient,
)

/** Input port that a future OrderConfirmedEvent consumer will invoke. */
fun interface SendOrderConfirmedNotificationUseCase {
    /** Sends the confirmation notification or propagates a technical delivery failure. */
    fun send(command: SendOrderConfirmedNotificationCommand)
}

/** Input port that a future OrderCancelledEvent consumer will invoke. */
fun interface SendOrderCancelledNotificationUseCase {
    /** Sends the cancellation notification or propagates a technical delivery failure. */
    fun send(command: SendOrderCancelledNotificationCommand)
}
