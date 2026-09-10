package com.orderflow.notification.application.service

import com.orderflow.notification.application.port.`in`.SendOrderConfirmedNotificationCommand
import com.orderflow.notification.application.port.`in`.SendOrderConfirmedNotificationUseCase
import com.orderflow.notification.application.port.`out`.NotificationSender
import org.springframework.stereotype.Service

/**
 * Application service that coordinates the order-confirmed notification use case.
 *
 * The service performs exactly two application-level steps: it asks [OrderNotificationFactory] to
 * create provider-neutral content and passes that content to [NotificationSender]. It contains no
 * Kafka deserialization, email API calls, logging, or persistence logic.
 *
 * [Service] makes this implementation available to Spring as the concrete bean for
 * [SendOrderConfirmedNotificationUseCase]. Constructor injection makes both dependencies explicit
 * and allows unit tests to supply a mocked sender without starting Spring.
 *
 * @property notificationFactory creates deterministic confirmation content.
 * @property notificationSender outbound delivery boundary implemented by an external adapter.
 */
@Service
class SendOrderConfirmedNotificationService(
    private val notificationFactory: OrderNotificationFactory,
    private val notificationSender: NotificationSender,
) : SendOrderConfirmedNotificationUseCase {
    /**
     * Executes one confirmation-notification request.
     *
     * A successful return means the configured sender completed normally. Delivery exceptions are
     * intentionally not caught or converted here; preserving the technical failure allows a future
     * Kafka consumer to own retries and acknowledgement behavior.
     *
     * @param command validated order and recipient data from the input boundary.
     * @throws com.orderflow.notification.application.port.out.NotificationDeliveryException when
     * the outbound adapter reports a technical delivery failure.
     */
    override fun send(command: SendOrderConfirmedNotificationCommand) {
        // Translate the use-case command into the complete model required by the outbound port.
        val notification = notificationFactory.confirmed(command.orderId, command.recipient)

        // Delegate delivery through the port; this service has no knowledge of the fake email adapter.
        notificationSender.send(notification)
    }
}
