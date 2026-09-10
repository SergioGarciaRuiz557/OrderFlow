package com.orderflow.notification.application.port.`in`

import com.orderflow.notification.domain.model.OrderId
import com.orderflow.notification.domain.model.Recipient

/**
 * Framework- and transport-independent request to notify a customer of order confirmation.
 *
 * A future Kafka adapter will map `OrderConfirmedEvent` data into this command. Keeping the command
 * free of Kafka classes means the application contract can also be invoked from tests or another
 * inbound adapter without changing the use case.
 *
 * @property orderId confirmed order to mention in the message.
 * @property recipient customer who must receive the message.
 */
data class SendOrderConfirmedNotificationCommand(
    val orderId: OrderId,
    val recipient: Recipient,
)

/**
 * Framework- and transport-independent request to notify a customer of order cancellation.
 *
 * The separate command gives cancellation an explicit application vocabulary and prevents the
 * input boundary from depending on a generic action flag or on a transport event type.
 *
 * @property orderId cancelled order to mention in the message.
 * @property recipient customer who must receive the message.
 */
data class SendOrderCancelledNotificationCommand(
    val orderId: OrderId,
    val recipient: Recipient,
)

/**
 * Primary input port for the order-confirmed notification workflow.
 *
 * Inbound technology depends on this interface rather than on the concrete application service.
 * Declaring it as a `fun interface` expresses that the boundary has one operation and also permits
 * lightweight lambda implementations where useful.
 */
fun interface SendOrderConfirmedNotificationUseCase {
    /**
     * Builds and delivers the confirmation notification represented by [command].
     *
     * @param command validated order and recipient values supplied by an inbound adapter.
     * @throws com.orderflow.notification.application.port.out.NotificationDeliveryException when
     * the selected outbound delivery mechanism fails technically.
     */
    fun send(command: SendOrderConfirmedNotificationCommand)
}

/**
 * Primary input port for the order-cancelled notification workflow.
 *
 * The port remains independent of Kafka so a future listener only needs to deserialize, map, and
 * invoke this contract. Notification behavior stays in the application layer.
 */
fun interface SendOrderCancelledNotificationUseCase {
    /**
     * Builds and delivers the cancellation notification represented by [command].
     *
     * @param command validated order and recipient values supplied by an inbound adapter.
     * @throws com.orderflow.notification.application.port.out.NotificationDeliveryException when
     * the selected outbound delivery mechanism fails technically.
     */
    fun send(command: SendOrderCancelledNotificationCommand)
}
