package com.orderflow.payment.adapter.`out`.external

import com.orderflow.payment.application.port.`out`.GatewayAuthorizationResult
import com.orderflow.payment.application.port.`out`.PaymentGatewayException
import com.orderflow.payment.application.port.`out`.PaymentGatewayRequest
import com.orderflow.payment.domain.model.Money
import com.orderflow.payment.domain.model.OrderId
import com.orderflow.payment.domain.model.PaymentMethodId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class FakePaymentGatewayTest {
    private val gateway = FakePaymentGateway()

    @Test
    fun `success method returns stable provider reference`() {
        val request = request("pm-test-success")

        val first = gateway.authorize(request)
        val second = gateway.authorize(request)

        assertEquals(first, second)
        assertTrue(first is GatewayAuthorizationResult.Authorized)
    }

    @Test
    fun `rejected method is a business result`() {
        assertTrue(gateway.authorize(request("pm-test-rejected")) is GatewayAuthorizationResult.Rejected)
    }

    @Test
    fun `error method is a technical exception`() {
        assertThrows(PaymentGatewayException::class.java) {
            gateway.authorize(request("pm-test-error"))
        }
    }

    private fun request(method: String) = PaymentGatewayRequest(
        OrderId("order-1"),
        OrderId("order-1"),
        Money.euros(BigDecimal("10.00")),
        PaymentMethodId(method),
    )
}
