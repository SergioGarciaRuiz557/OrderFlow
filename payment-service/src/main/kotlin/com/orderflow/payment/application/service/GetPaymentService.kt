package com.orderflow.payment.application.service

import com.orderflow.payment.application.port.`in`.GetPaymentUseCase
import com.orderflow.payment.application.port.`out`.PaymentRepository
import com.orderflow.payment.domain.model.Payment
import com.orderflow.payment.domain.model.PaymentId
import org.springframework.stereotype.Service

@Service
class GetPaymentService(private val repository: PaymentRepository) : GetPaymentUseCase {
    override fun getPayment(paymentId: PaymentId): Payment? = repository.findById(paymentId)
}
