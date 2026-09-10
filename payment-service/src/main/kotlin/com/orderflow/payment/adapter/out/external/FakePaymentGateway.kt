package com.orderflow.payment.adapter.`out`.external

import com.orderflow.payment.application.port.`out`.GatewayAuthorizationResult
import com.orderflow.payment.application.port.`out`.PaymentGateway
import com.orderflow.payment.application.port.`out`.PaymentGatewayException
import com.orderflow.payment.application.port.`out`.PaymentGatewayRequest
import com.orderflow.payment.domain.model.PaymentFailureReason
import com.orderflow.payment.domain.model.PaymentProviderReference
import org.springframework.stereotype.Component

/**
 * Deterministic development and test adapter for the [PaymentGateway] output port.
 *
 * This component simulates a provider without network access, secrets, randomness, or timing-based
 * behavior. Its result depends only on [PaymentGatewayRequest.paymentMethodId], which makes local
 * runs and automated tests reproducible. It demonstrates the required semantic boundary: a known
 * provider decline is returned as a business result, while an unavailable/failed provider throws a
 * technical [PaymentGatewayException].
 *
 * This adapter is not a REST endpoint and does not define the future inbound integration mechanism.
 * Replacing it with a real provider requires another adapter implementing the same port contract;
 * domain and application code do not change.
 */
@Component
class FakePaymentGateway : PaymentGateway {
    /**
     * Simulates provider authorization according to well-known development payment-method tokens.
     *
     * - `pm-test-success` returns an authorization reference derived from the stable idempotency key.
     * - `pm-test-rejected` returns a definitive business rejection.
     * - `pm-test-error` throws a technical exception and therefore leaves the payment retryable.
     * - Any other token is treated as a known unsupported-method business rejection.
     *
     * @param request provider-neutral request created by the application service.
     * @return deterministic successful or rejected business outcome.
     * @throws PaymentGatewayException only for the explicit technical-error test token.
     */
    override fun authorize(request: PaymentGatewayRequest): GatewayAuthorizationResult =
        // `when` is exhaustive for the intended fake scenarios and contains no random fallback.
        when (request.paymentMethodId.value) {
            "pm-test-success" -> GatewayAuthorizationResult.Authorized(
                // The stable order key makes repeated fake responses identical.
                PaymentProviderReference("fake-${request.idempotencyKey.value}"),
            )
            "pm-test-rejected" -> GatewayAuthorizationResult.Rejected(
                PaymentFailureReason("PAYMENT_METHOD_REJECTED"),
            )
            "pm-test-error" -> throw PaymentGatewayException("Fake payment gateway technical failure")
            else -> GatewayAuthorizationResult.Rejected(PaymentFailureReason("UNSUPPORTED_PAYMENT_METHOD"))
        }
}
