package com.orderflow.payment.application.service

import com.orderflow.payment.application.port.`in`.AuthorizePaymentCommand
import com.orderflow.payment.application.port.`in`.AuthorizePaymentUseCase
import com.orderflow.payment.application.port.`in`.PaymentAuthorizationResult
import com.orderflow.payment.application.port.`in`.PaymentMessagingUseCase
import com.orderflow.payment.application.port.`out`.PaymentEventPublisher
import org.springframework.stereotype.Service

@Service
class PaymentMessagingService(
    private val authorizePayment: AuthorizePaymentUseCase,
    private val events: PaymentEventPublisher,
) : PaymentMessagingUseCase {
    override fun authorize(command: AuthorizePaymentCommand) {
        when (val result = authorizePayment.authorize(command)) {
            is PaymentAuthorizationResult.Authorized -> events.paymentAuthorized(result.payment)
            is PaymentAuthorizationResult.Rejected -> events.paymentRejected(result.payment, result.reason)
        }
    }
}
