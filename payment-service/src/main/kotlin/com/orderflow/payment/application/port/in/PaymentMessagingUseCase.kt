package com.orderflow.payment.application.port.`in`

fun interface PaymentMessagingUseCase {
    fun authorize(command: AuthorizePaymentCommand)
}
