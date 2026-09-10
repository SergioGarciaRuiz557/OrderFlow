package com.orderflow.notification.adapter.`out`.external

import com.orderflow.notification.application.port.`out`.NotificationSender
import com.orderflow.notification.domain.model.Notification
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * Local outbound adapter that simulates email delivery without external infrastructure.
 *
 * The adapter is useful for development and architecture verification: it receives the exact model
 * a real email integration would receive, but records delivery through the application log instead
 * of contacting SMTP, SendGrid, SES, or another provider. It owns no message-building behavior.
 *
 * Replacing this component with an SMTP or email-provider adapter does not affect application or
 * domain code because both depend only on [NotificationSender]. [Component] registers this class as
 * the current Spring implementation of that output port.
 */
@Component
class FakeEmailNotificationSender : NotificationSender {
    /** SLF4J logger associated with this adapter class and configured by Spring Boot. */
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * Simulates successful email delivery by writing all relevant notification fields to the log.
     *
     * SLF4J placeholders are used instead of string concatenation so formatting occurs only when the
     * configured log level enables the statement. The method returns normally after logging, which
     * represents successful delivery in this deterministic fake.
     *
     * @param notification complete provider-neutral message created by the application service.
     */
    override fun send(notification: Notification) {
        // Log structured values separately so production log collectors can parse the delivery trace.
        logger.info(
            // Each `{}` placeholder is filled, in order, by the four arguments below.
            "Fake email delivered: recipient={}, type={}, orderId={}, message={}",
            // First placeholder: validated destination email address.
            notification.recipient.email,
            // Second placeholder: semantic confirmation or cancellation type.
            notification.type,
            // Third placeholder: order correlation identifier.
            notification.orderId.value,
            // Fourth placeholder: deterministic customer-facing text.
            notification.message,
        )
    }
}
