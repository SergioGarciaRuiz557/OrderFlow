package com.orderflow.payment.application.service

import com.orderflow.payment.application.port.`in`.AuthorizePaymentCommand
import com.orderflow.payment.application.port.`in`.AuthorizePaymentUseCase
import com.orderflow.payment.application.port.`out`.PaymentEventPublisher
import com.orderflow.payment.application.port.`out`.PaymentGatewayException
import com.orderflow.payment.domain.model.Money
import com.orderflow.payment.domain.model.OrderId
import com.orderflow.payment.domain.model.PaymentMethodId
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class PaymentMessagingServiceTest {
    @Test fun `el fallo técnico de autorización se propaga sin publicar un rechazo de negocio`() {
        val authorization = mockk<AuthorizePaymentUseCase>()
        val events = mockk<PaymentEventPublisher>(relaxed = true)
        val command = AuthorizePaymentCommand(OrderId("550e8400-e29b-41d4-a716-446655440000"),
            Money.euros(BigDecimal("10.00")), PaymentMethodId("pm-test-error"))
        every { authorization.authorize(command) } throws PaymentGatewayException("provider unavailable")

        assertThrows<PaymentGatewayException> { PaymentMessagingService(authorization, events).authorize(command) }
        verify(exactly = 0) { events.paymentAuthorized(any()) }
        verify(exactly = 0) { events.paymentRejected(any(), any()) }
    }
}
