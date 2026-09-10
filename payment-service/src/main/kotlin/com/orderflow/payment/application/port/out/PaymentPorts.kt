package com.orderflow.payment.application.port.`out`

import com.orderflow.payment.domain.model.Money
import com.orderflow.payment.domain.model.OrderId
import com.orderflow.payment.domain.model.Payment
import com.orderflow.payment.domain.model.PaymentFailureReason
import com.orderflow.payment.domain.model.PaymentId
import com.orderflow.payment.domain.model.PaymentMethodId
import com.orderflow.payment.domain.model.PaymentProviderReference
import java.time.Instant

/**
 * Framework-independent persistence port for complete [Payment] aggregates.
 *
 * Application code depends only on this contract. The Spring Data repository and mutable JPA entity
 * remain private details of the persistence adapter, preserving the hexagonal dependency direction.
 */
interface PaymentRepository {
    /**
     * Retrieves an aggregate by its technical identity.
     *
     * @return stored payment or `null` when it does not exist.
     */
    fun findById(paymentId: PaymentId): Payment?

    /**
     * Retrieves the unique payment operation associated with an order.
     *
     * This lookup is the first application-level idempotency check before any gateway invocation.
     *
     * @return stored payment for the order, or `null` when authorization has never been requested.
     */
    fun findByOrderId(orderId: OrderId): Payment?

    /**
     * Inserts or updates the complete aggregate snapshot.
     *
     * @param payment validated domain state to persist.
     * @return persisted snapshot including the current optimistic-lock version.
     * @throws DuplicatePaymentException when a database uniqueness rule is violated.
     */
    fun save(payment: Payment): Payment
}

/**
 * Provider-neutral authorization request sent through [PaymentGateway].
 *
 * The gateway idempotency key intentionally uses [OrderId]. It remains stable across service retries
 * and even across a local transaction rollback, preventing a provider that honors idempotency keys
 * from creating a second charge after an uncertain response.
 *
 * @property idempotencyKey stable business-operation key required by gateway implementations.
 * @property orderId order being paid, included separately as business request context.
 * @property amount amount and currency to authorize.
 * @property paymentMethodId opaque provider payment-method token.
 */
data class PaymentGatewayRequest(
    val idempotencyKey: OrderId,
    val orderId: OrderId,
    val amount: Money,
    val paymentMethodId: PaymentMethodId,
)

/**
 * Exhaustive set of definitive business decisions that a gateway may return.
 *
 * Technical inability to obtain a decision is deliberately absent and must be represented by
 * [PaymentGatewayException]. This separation controls future retry versus rejection-event behavior.
 */
sealed interface GatewayAuthorizationResult {
    /**
     * Provider approval.
     *
     * @property providerReference traceable reference that must be stored with the aggregate.
     */
    data class Authorized(val providerReference: PaymentProviderReference) : GatewayAuthorizationResult

    /**
     * Known provider business rejection.
     *
     * @property reason machine-readable reason that will become aggregate state.
     */
    data class Rejected(val reason: PaymentFailureReason) : GatewayAuthorizationResult
}

/**
 * Output port that isolates payment orchestration from a concrete external provider.
 *
 * Real adapters must forward [PaymentGatewayRequest.idempotencyKey] using their provider's native
 * idempotency mechanism. They must map known declines to [GatewayAuthorizationResult.Rejected] and
 * throw [PaymentGatewayException] when the decision is unknown because of infrastructure failure.
 */
fun interface PaymentGateway {
    /**
     * Requests authorization without exposing provider SDK types to application or domain code.
     *
     * @param request validated, provider-neutral authorization data.
     * @return definitive authorization or business rejection.
     * @throws PaymentGatewayException for timeouts, unavailable providers, malformed responses, or
     * other technical conditions where no trustworthy business outcome exists.
     */
    @Throws(PaymentGatewayException::class)
    fun authorize(request: PaymentGatewayRequest): GatewayAuthorizationResult
}

/**
 * Technical boundary for failures while communicating with a payment provider.
 *
 * Unlike [GatewayAuthorizationResult.Rejected], this exception does not change aggregate state to
 * `REJECTED`. The application ensures a pending record is committed, then propagates the exception
 * so a future message consumer can retry with the same idempotency key.
 *
 * @param message diagnostic summary safe for application logs.
 * @param cause underlying provider SDK or transport exception, when available.
 */
class PaymentGatewayException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

/**
 * Small output port for obtaining authoritative timestamps without coupling application tests to
 * the system clock.
 */
fun interface ClockProvider {
    /** @return current UTC instant normalized to the precision used by PostgreSQL. */
    fun now(): Instant
}

/**
 * Cross-instance mutual-exclusion boundary for one order's authorization workflow.
 *
 * The whole read/create/gateway/write sequence executes inside [operation]. Serializing equal
 * [OrderId] values prevents concurrent requests from both passing the initial absence check and
 * calling the provider. Different orders may execute concurrently.
 */
interface PaymentAuthorizationLock {
    /**
     * Executes [operation] while holding the lock associated with [orderId].
     *
     * @param orderId lock key and payment business idempotency key.
     * @param operation non-null-producing authorization workflow protected by the lock.
     * @return result produced by [operation].
     */
    fun <T : Any> withLock(orderId: OrderId, operation: () -> T): T
}

/**
 * Application-facing translation of a PostgreSQL uniqueness violation while saving a payment.
 *
 * Keeping the Spring [org.springframework.dao.DataIntegrityViolationException] out of the port
 * contract prevents core code and tests from depending on a persistence framework.
 *
 * @param cause original infrastructure exception retained for diagnosis.
 */
class DuplicatePaymentException(cause: Throwable) : RuntimeException("A payment already exists for the order", cause)
