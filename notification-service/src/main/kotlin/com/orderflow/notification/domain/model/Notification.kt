package com.orderflow.notification.domain.model

/**
 * Strongly typed identifier of the order about which the customer is being notified.
 *
 * A dedicated type prevents a plain string with a different meaning, such as an email address,
 * from being passed accidentally where an order identifier is expected. [JvmInline] keeps this
 * distinction at compile time without normally allocating an additional wrapper object at runtime.
 *
 * @property value external order identifier carried by the final order lifecycle event.
 * @throws IllegalArgumentException when [value] is empty or contains only whitespace.
 */
@JvmInline
value class OrderId(val value: String) {
    /** Enforces the only order-id invariant owned by Notification Service. */
    init {
        // `require` rejects invalid input immediately instead of allowing an unusable notification.
        require(value.isNotBlank()) { "Order id must not be blank" }
    }
}

/**
 * Email recipient for a notification.
 *
 * Validation deliberately checks only the assumptions needed by this service. Provider-specific
 * and RFC-complete validation belongs at a future delivery boundary. [JvmInline] provides type
 * safety while retaining the runtime efficiency of the wrapped string in common call sites.
 *
 * @property email destination email address preserved exactly as received from the application
 * command.
 * @throws IllegalArgumentException when [email] does not satisfy the intentionally basic format.
 */
@JvmInline
value class Recipient(val email: String) {
    /** Prevents clearly invalid recipient data from reaching a delivery adapter. */
    init {
        // The regular expression checks for text on both sides of `@` and a dotted domain.
        require(BASIC_EMAIL.matches(email)) { "Recipient must contain a valid email address" }
    }

    /** Validation implementation shared by every [Recipient] construction. */
    private companion object {
        /**
         * Deliberately small email check: no whitespace, one address separator, and a dotted domain.
         * It is not intended to reproduce the complete email RFC or provider-specific rules.
         */
        val BASIC_EMAIL = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")
    }
}

/**
 * Closed set of customer-facing order lifecycle messages currently supported by this service.
 *
 * The enum travels through the provider-neutral [Notification] model. Delivery adapters can use it
 * for logging or provider mapping without receiving Kafka event names or other transport concepts.
 */
enum class NotificationType {
    /** The order completed the confirmation flow successfully. */
    ORDER_CONFIRMED,

    /** The order reached its final cancelled state. */
    ORDER_CANCELLED,
}

/**
 * Immutable, provider-neutral message ready to be delivered to a customer.
 *
 * This data class is intentionally not an aggregate. The service does not persist notifications or
 * manage a delivery lifecycle, so the model only carries the information required by
 * `NotificationSender`. Structural equality supplied by `data class` also makes the contract easy
 * to verify in application tests.
 *
 * @property orderId order whose final lifecycle change produced the notification.
 * @property recipient customer email destination.
 * @property type semantic kind of notification being delivered.
 * @property message deterministic customer-facing text built by the application layer.
 * @throws IllegalArgumentException when [message] is empty or contains only whitespace.
 */
data class Notification(
    val orderId: OrderId,
    val recipient: Recipient,
    val type: NotificationType,
    val message: String,
) {
    /** Ensures every outbound notification contains useful customer-facing content. */
    init {
        // A blank message would be technically sendable but has no valid business purpose.
        require(message.isNotBlank()) { "Notification message must not be blank" }
    }
}
