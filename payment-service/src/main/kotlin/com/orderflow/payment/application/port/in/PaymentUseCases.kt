package com.orderflow.payment.application.port.`in`

import com.orderflow.payment.domain.model.Money
import com.orderflow.payment.domain.model.OrderId
import com.orderflow.payment.domain.model.Payment
import com.orderflow.payment.domain.model.PaymentFailureReason
import com.orderflow.payment.domain.model.PaymentId
import com.orderflow.payment.domain.model.PaymentMethodId

/**
 * Framework-independent input required to authorize one order payment.
 *
 * A future Kafka adapter will deserialize transport data and construct this command before invoking
 * [AuthorizePaymentUseCase]. Using domain value objects at the input-port boundary guarantees that
 * blank identifiers, unsupported money, and invalid scale are rejected before orchestration begins.
 *
 * @property orderId order and business payment operation to authorize.
 * @property amount explicit monetary value expected by the order.
 * @property paymentMethodId provider token identifying the customer's payment instrument.
 */
data class AuthorizePaymentCommand(
    val orderId: OrderId,
    val amount: Money,
    val paymentMethodId: PaymentMethodId,
)

/**
 * Exhaustive business outcome returned by payment authorization.
 *
 * Only definitive provider decisions appear here. Infrastructure failures are propagated as
 * [com.orderflow.payment.application.port.out.PaymentGatewayException], because a caller must retry
 * them rather than publish a rejection. The sealed hierarchy makes every caller handle authorization
 * and rejection explicitly with an exhaustive `when` expression.
 */
sealed interface PaymentAuthorizationResult {
    /** Aggregate snapshot containing the definitive outcome. */
    val payment: Payment

    /**
     * Indicates whether the response came from an earlier completed authorization.
     *
     * `true` means the gateway was not called and the database was not rewritten during this request.
     */
    val alreadyProcessed: Boolean

    /**
     * Successful business outcome.
     *
     * @property payment authorized aggregate containing its provider reference.
     * @property alreadyProcessed `true` for an idempotent replay of prior success.
     */
    data class Authorized(
        override val payment: Payment,
        override val alreadyProcessed: Boolean,
    ) : PaymentAuthorizationResult

    /**
     * Definitive business rejection, such as a declined payment instrument.
     *
     * @property payment rejected aggregate containing the same reason.
     * @property reason stable business reason suitable for a future rejection event.
     * @property alreadyProcessed `true` for an idempotent replay of the original rejection.
     */
    data class Rejected(
        override val payment: Payment,
        val reason: PaymentFailureReason,
        override val alreadyProcessed: Boolean,
    ) : PaymentAuthorizationResult
}

/**
 * Primary input port for processing a payment authorization.
 *
 * Inbound technologies depend on this interface rather than on [com.orderflow.payment.application.service.AuthorizePaymentService],
 * keeping Kafka, tests, or another future adapter replaceable without altering the business workflow.
 */
fun interface AuthorizePaymentUseCase {
    /**
     * Authorizes a new payment or returns the stored outcome for an exact duplicate.
     *
     * @param command validated business request.
     * @return authorized or rejected definitive business result.
     * @throws com.orderflow.payment.application.service.PaymentRequestConflictException when the
     * order already has a payment with different immutable request data.
     * @throws com.orderflow.payment.application.port.out.PaymentGatewayException when no definitive
     * provider decision can be obtained.
     */
    fun authorize(command: AuthorizePaymentCommand): PaymentAuthorizationResult
}

/** Read-only input port for retrieving the current snapshot of a payment aggregate. */
fun interface GetPaymentUseCase {
    /**
     * Looks up a payment by its aggregate identifier.
     *
     * @param paymentId identifier assigned when the payment was created.
     * @return persisted payment or `null` when the identifier is unknown.
     */
    fun getPayment(paymentId: PaymentId): Payment?
}
