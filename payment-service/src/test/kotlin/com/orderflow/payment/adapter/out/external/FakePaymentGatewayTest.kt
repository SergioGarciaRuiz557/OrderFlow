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

/**
 * Standalone contract tests for the deterministic fake gateway adapter.
 *
 * These tests prove the fake distinguishes provider business outcomes from technical failures and
 * remains deterministic without requiring Spring, PostgreSQL, or external network access.
 */
class FakePaymentGatewayTest {
    /** Stateless adapter under test, constructed directly rather than through Spring. */
    private val gateway = FakePaymentGateway()

    /** Verifies equal idempotent requests always produce the same successful provider reference. */
    @Test
    fun `success method returns stable provider reference`() {
        val request = request("pm-test-success")

        val first = gateway.authorize(request)
        val second = gateway.authorize(request)

        assertEquals(first, second)
        assertTrue(first is GatewayAuthorizationResult.Authorized)
    }

    /** Verifies the configured decline token returns a normal business result instead of throwing. */
    @Test
    fun `rejected method is a business result`() {
        assertTrue(gateway.authorize(request("pm-test-rejected")) is GatewayAuthorizationResult.Rejected)
    }

    /** Verifies the configured infrastructure-error token crosses the technical exception boundary. */
    @Test
    fun `error method is a technical exception`() {
        assertThrows(PaymentGatewayException::class.java) {
            gateway.authorize(request("pm-test-error"))
        }
    }

    /**
     * Creates a valid provider-neutral request while varying only the scenario-driving method token.
     *
     * @param method fake payment-method token selecting the expected behavior.
     */
    private fun request(method: String) = PaymentGatewayRequest(
        OrderId("order-1"),
        OrderId("order-1"),
        Money.euros(BigDecimal("10.00")),
        PaymentMethodId(method),
    )
}
