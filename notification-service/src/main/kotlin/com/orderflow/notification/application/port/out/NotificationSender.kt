package com.orderflow.notification.application.port.`out`

import com.orderflow.notification.domain.model.Notification

/**
 * Provider-neutral output port for delivering a customer notification.
 *
 * Application services own *when* and *what* to notify but do not know *how* delivery occurs. An
 * adapter supplies that implementation, currently through `FakeEmailNotificationSender` and later
 * potentially through SMTP or an external email provider. This dependency direction keeps external
 * technology outside the application core.
 *
 * The single operation makes this a `fun interface`, keeping the contract focused and allowing
 * concise test doubles or alternative adapters.
 */
fun interface NotificationSender {
    /**
     * Delivers [notification].
     *
     * Implementations throw [NotificationDeliveryException] when delivery fails technically. The
     * application intentionally propagates that failure so a future message consumer can retry.
     *
     * @param notification complete provider-neutral message to deliver.
     * @throws NotificationDeliveryException when the adapter cannot complete delivery for a
     * technical reason such as provider unavailability.
     */
    fun send(notification: Notification)
}

/**
 * Technical delivery failure reported by a [NotificationSender] adapter.
 *
 * This exception belongs to the notification context and is deliberately distinct from order
 * business outcomes. Application services let it propagate so a future asynchronous consumer can
 * retry or route the event to a dead-letter mechanism instead of treating a provider outage as an
 * order cancellation.
 *
 * @param message human-readable diagnostic summary of the failure.
 * @param cause optional original provider or network exception retained for diagnostics.
 */
class NotificationDeliveryException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
