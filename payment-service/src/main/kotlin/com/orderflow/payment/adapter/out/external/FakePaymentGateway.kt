package com.orderflow.payment.adapter.`out`.external

import com.orderflow.payment.application.port.`out`.GatewayAuthorizationResult
import com.orderflow.payment.application.port.`out`.PaymentGateway
import com.orderflow.payment.application.port.`out`.PaymentGatewayException
import com.orderflow.payment.application.port.`out`.PaymentGatewayRequest
import com.orderflow.payment.domain.model.PaymentFailureReason
import com.orderflow.payment.domain.model.PaymentProviderReference
import org.springframework.stereotype.Component

@Component
class FakePaymentGateway : PaymentGateway {
    override fun authorize(request: PaymentGatewayRequest): GatewayAuthorizationResult =
        when (request.paymentMethodId.value) {
            "pm-test-success" -> GatewayAuthorizationResult.Authorized(
                PaymentProviderReference("fake-${request.idempotencyKey.value}"),
            )
            "pm-test-rejected" -> GatewayAuthorizationResult.Rejected(
                PaymentFailureReason("PAYMENT_METHOD_REJECTED"),
            )
            "pm-test-error" -> throw PaymentGatewayException("Fake payment gateway technical failure")
            else -> GatewayAuthorizationResult.Rejected(PaymentFailureReason("UNSUPPORTED_PAYMENT_METHOD"))
        }
}
