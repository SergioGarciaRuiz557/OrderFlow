package com.orderflow.notification.application.service

import com.orderflow.notification.application.port.`in`.SendOrderCancelledNotificationCommand
import com.orderflow.notification.application.port.`in`.SendOrderCancelledNotificationUseCase
import com.orderflow.notification.application.port.`out`.NotificationSender
import org.springframework.stereotype.Service

/**
 * Application service that coordinates the order-cancelled notification use case.
 *
 * The service converts an input command into a complete notification through
 * [OrderNotificationFactory], then delegates delivery to [NotificationSender]. Keeping these steps
 * here ensures a future Kafka listener remains a thin inbound adapter and the email mechanism stays
 * isolated behind an output port.
 *
 * [Service] registers the implementation as the Spring bean for
 * [SendOrderCancelledNotificationUseCase]. Constructor injection documents its dependencies and
 * makes the class directly unit-testable.
 *
 * @property notificationFactory creates deterministic cancellation content.
 * @property notificationSender outbound delivery boundary implemented by an external adapter.
 */
@Service
class SendOrderCancelledNotificationService(
    private val notificationFactory: OrderNotificationFactory,
    private val notificationSender: NotificationSender,
) : SendOrderCancelledNotificationUseCase {
    /**
     * Executes one cancellation-notification request.
     *
     * No notification status is stored because the service is intentionally stateless. If delivery
     * fails, the sender's technical exception propagates unchanged so the eventual asynchronous
     * inbound adapter can apply its retry policy.
     *
     * @param command validated order and recipient data from the input boundary.
     * @throws com.orderflow.notification.application.port.out.NotificationDeliveryException when
     * the outbound adapter reports a technical delivery failure.
     */
    override fun send(command: SendOrderCancelledNotificationCommand) {
        // Build the structured cancellation message without exposing delivery technology.
        val notification = notificationFactory.cancelled(command.orderId, command.recipient)

        // The output port selects the configured adapter at runtime through dependency injection.
        notificationSender.send(notification)
    }
}
