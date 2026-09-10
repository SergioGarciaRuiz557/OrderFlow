package com.orderflow.payment.application.service

import com.orderflow.payment.application.port.`in`.AuthorizePaymentCommand
import com.orderflow.payment.application.port.`in`.AuthorizePaymentUseCase
import com.orderflow.payment.application.port.`in`.PaymentAuthorizationResult
import com.orderflow.payment.application.port.`out`.ClockProvider
import com.orderflow.payment.application.port.`out`.GatewayAuthorizationResult
import com.orderflow.payment.application.port.`out`.PaymentAuthorizationLock
import com.orderflow.payment.application.port.`out`.PaymentGateway
import com.orderflow.payment.application.port.`out`.PaymentGatewayException
import com.orderflow.payment.application.port.`out`.PaymentGatewayRequest
import com.orderflow.payment.application.port.`out`.PaymentRepository
import com.orderflow.payment.domain.model.Payment
import com.orderflow.payment.domain.model.PaymentId
import com.orderflow.payment.domain.model.PaymentStatus
import org.springframework.stereotype.Service

/**
 * Application service that coordinates the complete payment authorization use case.
 *
 * The service contains orchestration, not provider or persistence details. It serializes work for
 * the order, locates or creates the aggregate, delegates the external decision to [PaymentGateway],
 * invokes behavior on [Payment], and persists the resulting snapshot. Business invariants remain in
 * the aggregate, while idempotency decisions that require repository access belong here.
 *
 * Technical gateway failures receive special transaction handling: the service converts the thrown
 * exception into a temporary internal value while inside [PaymentAuthorizationLock]. This lets the
 * lock adapter commit the newly created `PENDING` payment. Immediately after that commit, the same
 * exception is rethrown to the caller. The result stays retryable without disguising infrastructure
 * failure as a business rejection.
 *
 * @property repository domain-facing access to persisted payment aggregates.
 * @property gateway provider-neutral outbound authorization boundary.
 * @property clock deterministic source of creation and transition timestamps.
 * @property authorizationLock cross-instance serialization for the same order.
 */
@Service
class AuthorizePaymentService(
    private val repository: PaymentRepository,
    private val gateway: PaymentGateway,
    private val clock: ClockProvider,
    private val authorizationLock: PaymentAuthorizationLock,
) : AuthorizePaymentUseCase {

    /**
     * Processes a new authorization or returns a previously persisted definitive result.
     *
     * The lock spans repository reads, the gateway call, and repository writes. This is intentional:
     * without it, two simultaneous commands for a previously unseen order could both call the
     * external provider before the database uniqueness constraint rejects one local insert.
     *
     * A [PaymentGatewayException] is caught only inside the transaction callback. Returning the
     * [AuthorizationExecution.TechnicalFailure] marker makes the callback complete normally and
     * therefore commits `PENDING`; the exhaustive outer `when` then restores the public exception
     * contract after the lock transaction has ended.
     *
     * @param command validated order, amount, and payment-method data.
     * @return a definitive authorized or rejected business result.
     * @throws PaymentGatewayException after pending retry state has been committed.
     * @throws PaymentRequestConflictException if an order is repeated with changed request data.
     */
    override fun authorize(command: AuthorizePaymentCommand): PaymentAuthorizationResult =
        // PostgreSQL uses command.orderId to serialize only competing work for this business payment.
        when (val execution = authorizationLock.withLock(command.orderId) {
            try {
                AuthorizationExecution.Completed(authorizeWithinLock(command))
            } catch (exception: PaymentGatewayException) {
                // A normal callback return commits PENDING; the exception is rethrown outside below.
                AuthorizationExecution.TechnicalFailure(exception)
            }
        }) {
            // Sealing the internal result prevents a future branch from being forgotten here.
            is AuthorizationExecution.Completed -> execution.result
            is AuthorizationExecution.TechnicalFailure -> throw execution.exception
        }

    /**
     * Performs all state-dependent decisions while the order-scoped lock is held.
     *
     * Evaluation order is significant:
     * 1. Existing state is loaded by the order business key.
     * 2. A repeated command must have the original amount and method.
     * 3. An absent payment is created and flushed as pending before the gateway is called.
     * 4. Terminal payments return idempotently; only pending payments reach the provider.
     *
     * @param command authorization request currently protected by the order lock.
     * @return definitive business result, unless the gateway throws a technical exception.
     */
    private fun authorizeWithinLock(command: AuthorizePaymentCommand): PaymentAuthorizationResult {
        // `findByOrderId` is the application idempotency check performed before any external call.
        val payment = repository.findByOrderId(command.orderId)
            ?.also { existing ->
                // Same order with different immutable intent is a conflict, not an idempotent retry.
                if (!existing.matches(command.amount, command.paymentMethodId)) {
                    throw PaymentRequestConflictException(command.orderId.value)
                }
            }
            // The Elvis branch runs only for the first authorization request for this order.
            ?: repository.save(
                Payment.pending(
                    id = PaymentId.new(),
                    orderId = command.orderId,
                    amount = command.amount,
                    paymentMethodId = command.paymentMethodId,
                    createdAt = clock.now(),
                ),
            )

        // Terminal outcomes never call the provider or write again; pending is the sole active state.
        return when (payment.status) {
            PaymentStatus.AUTHORIZED -> PaymentAuthorizationResult.Authorized(payment, alreadyProcessed = true)
            PaymentStatus.REJECTED -> PaymentAuthorizationResult.Rejected(
                payment,
                // Aggregate invariants guarantee a rejected payment always contains this value.
                requireNotNull(payment.failureReason),
                alreadyProcessed = true,
            )
            PaymentStatus.PENDING -> authorizePending(payment)
        }
    }

    /**
     * Calls the provider for a pending aggregate and persists its definitive domain transition.
     *
     * [OrderId] is used as the provider idempotency key. Even if the provider responds successfully
     * but local persistence later fails, replaying the command uses the same key and must not create
     * another charge in a conforming gateway implementation.
     *
     * @param payment persisted pending aggregate whose immutable request data is sent to the gateway.
     * @return persisted authorized or rejected application result with `alreadyProcessed = false`.
     * @throws PaymentGatewayException when the provider does not return a definitive decision.
     */
    private fun authorizePending(payment: Payment): PaymentAuthorizationResult {
        // This DTO is the only representation visible to an external-provider adapter.
        val request = PaymentGatewayRequest(
            idempotencyKey = payment.orderId,
            orderId = payment.orderId,
            amount = payment.amount,
            paymentMethodId = payment.paymentMethodId,
        )

        // The sealed result keeps normal business outcomes explicit and exhaustive.
        return when (val outcome = gateway.authorize(request)) {
            is GatewayAuthorizationResult.Authorized -> {
                // Domain behavior validates the transition before the new snapshot reaches JPA.
                val saved = repository.save(payment.authorize(outcome.providerReference, clock.now()))
                PaymentAuthorizationResult.Authorized(saved, alreadyProcessed = false)
            }
            is GatewayAuthorizationResult.Rejected -> {
                // A known decline is persisted; this branch is never used for technical exceptions.
                val saved = repository.save(payment.reject(outcome.reason, clock.now()))
                PaymentAuthorizationResult.Rejected(saved, outcome.reason, alreadyProcessed = false)
            }
        }
    }

    /**
     * Internal transaction-control result used only to delay technical exception propagation.
     *
     * It is not part of the public use-case contract and never converts a technical failure into a
     * business result. Its sole purpose is to let the lock callback return normally so pending state
     * commits before [TechnicalFailure.exception] is rethrown.
     */
    private sealed interface AuthorizationExecution {
        /** Normal callback completion carrying the public use-case result. */
        data class Completed(val result: PaymentAuthorizationResult) : AuthorizationExecution

        /** Deferred technical failure that must be rethrown immediately after transaction commit. */
        data class TechnicalFailure(val exception: PaymentGatewayException) : AuthorizationExecution
    }
}

/**
 * Signals reuse of an order id with a different amount or payment method.
 *
 * Silently treating changed intent as a retry would either return the wrong historical result or
 * risk charging unexpected data. The application therefore stops before the gateway is called.
 *
 * @param orderId external order value included in the diagnostic message.
 */
class PaymentRequestConflictException(orderId: String) :
    RuntimeException("Order $orderId already has a different payment request")
