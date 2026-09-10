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

@Service
class AuthorizePaymentService(
    private val repository: PaymentRepository,
    private val gateway: PaymentGateway,
    private val clock: ClockProvider,
    private val authorizationLock: PaymentAuthorizationLock,
) : AuthorizePaymentUseCase {

    override fun authorize(command: AuthorizePaymentCommand): PaymentAuthorizationResult =
        when (val execution = authorizationLock.withLock(command.orderId) {
            try {
                AuthorizationExecution.Completed(authorizeWithinLock(command))
            } catch (exception: PaymentGatewayException) {
                // Return normally so the transaction commits the PENDING payment before propagation.
                AuthorizationExecution.TechnicalFailure(exception)
            }
        }) {
            is AuthorizationExecution.Completed -> execution.result
            is AuthorizationExecution.TechnicalFailure -> throw execution.exception
        }

    private fun authorizeWithinLock(command: AuthorizePaymentCommand): PaymentAuthorizationResult {
        val payment = repository.findByOrderId(command.orderId)
            ?.also { existing ->
                if (!existing.matches(command.amount, command.paymentMethodId)) {
                    throw PaymentRequestConflictException(command.orderId.value)
                }
            }
            ?: repository.save(
                Payment.pending(
                    id = PaymentId.new(),
                    orderId = command.orderId,
                    amount = command.amount,
                    paymentMethodId = command.paymentMethodId,
                    createdAt = clock.now(),
                ),
            )

        return when (payment.status) {
            PaymentStatus.AUTHORIZED -> PaymentAuthorizationResult.Authorized(payment, alreadyProcessed = true)
            PaymentStatus.REJECTED -> PaymentAuthorizationResult.Rejected(
                payment,
                requireNotNull(payment.failureReason),
                alreadyProcessed = true,
            )
            PaymentStatus.PENDING -> authorizePending(payment)
        }
    }

    private fun authorizePending(payment: Payment): PaymentAuthorizationResult {
        val request = PaymentGatewayRequest(
            idempotencyKey = payment.orderId,
            orderId = payment.orderId,
            amount = payment.amount,
            paymentMethodId = payment.paymentMethodId,
        )
        return when (val outcome = gateway.authorize(request)) {
            is GatewayAuthorizationResult.Authorized -> {
                val saved = repository.save(payment.authorize(outcome.providerReference, clock.now()))
                PaymentAuthorizationResult.Authorized(saved, alreadyProcessed = false)
            }
            is GatewayAuthorizationResult.Rejected -> {
                val saved = repository.save(payment.reject(outcome.reason, clock.now()))
                PaymentAuthorizationResult.Rejected(saved, outcome.reason, alreadyProcessed = false)
            }
        }
    }

    private sealed interface AuthorizationExecution {
        data class Completed(val result: PaymentAuthorizationResult) : AuthorizationExecution
        data class TechnicalFailure(val exception: PaymentGatewayException) : AuthorizationExecution
    }
}

class PaymentRequestConflictException(orderId: String) :
    RuntimeException("Order $orderId already has a different payment request")
