package com.orderflow.notification.domain.model

/** Identifier of the order about which a customer is being notified. */
@JvmInline
value class OrderId(val value: String) {
    init {
        require(value.isNotBlank()) { "Order id must not be blank" }
    }
}

/**
 * Email recipient for a notification.
 *
 * Validation deliberately checks only the assumptions needed by this service. Provider-specific
 * and RFC-complete validation belongs at a future delivery boundary.
 */
@JvmInline
value class Recipient(val email: String) {
    init {
        require(BASIC_EMAIL.matches(email)) { "Recipient must contain a valid email address" }
    }

    private companion object {
        val BASIC_EMAIL = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")
    }
}

/** Customer-facing order lifecycle notification supported by this bounded context. */
enum class NotificationType {
    ORDER_CONFIRMED,
    ORDER_CANCELLED,
}

/** Immutable, provider-neutral notification passed to a delivery adapter. */
data class Notification(
    val orderId: OrderId,
    val recipient: Recipient,
    val type: NotificationType,
    val message: String,
) {
    init {
        require(message.isNotBlank()) { "Notification message must not be blank" }
    }
}
