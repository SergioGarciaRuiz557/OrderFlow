package com.orderflow.notification.application.port.`out`

import com.orderflow.notification.domain.model.Notification

/** Provider-neutral output port for delivering a customer notification. */
fun interface NotificationSender {
    /**
     * Delivers [notification].
     *
     * Implementations throw [NotificationDeliveryException] when delivery fails technically. The
     * application intentionally propagates that failure so a future message consumer can retry.
     */
    fun send(notification: Notification)
}

/** Technical failure reported by a notification delivery adapter. */
class NotificationDeliveryException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
