package com.orderflow.notification.adapter.`out`.external

import com.orderflow.notification.application.port.`out`.NotificationSender
import com.orderflow.notification.domain.model.Notification
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * Local outbound adapter that simulates email delivery without external infrastructure.
 *
 * Replacing this component with an SMTP or email-provider adapter does not affect application or
 * domain code because both depend only on [NotificationSender].
 */
@Component
class FakeEmailNotificationSender : NotificationSender {
    private val logger = LoggerFactory.getLogger(javaClass)

    override fun send(notification: Notification) {
        logger.info(
            "Fake email delivered: recipient={}, type={}, orderId={}, message={}",
            notification.recipient.email,
            notification.type,
            notification.orderId.value,
            notification.message,
        )
    }
}
