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
 * Pruebas de contrato independientes para el adaptador determinista de la pasarela simulada.
 *
 * Estas pruebas demuestran que la simulación distingue los resultados de negocio del proveedor de
 * los fallos técnicos y que permanece determinista sin requerir Spring, PostgreSQL ni acceso externo a la red.
 */
class FakePaymentGatewayTest {
    /** Adaptador sin estado sometido a prueba, construido directamente en lugar de mediante Spring. */
    private val gateway = FakePaymentGateway()

    /** Verifica que las solicitudes idempotentes iguales siempre produzcan la misma referencia satisfactoria del proveedor. */
    @Test
    fun `el método satisfactorio devuelve una referencia estable del proveedor`() {
        val request = request("pm-test-success")

        val first = gateway.authorize(request)
        val second = gateway.authorize(request)

        assertEquals(first, second)
        assertTrue(first is GatewayAuthorizationResult.Authorized)
    }

    /** Verifica que el token de rechazo configurado devuelva un resultado de negocio normal en lugar de lanzar una excepción. */
    @Test
    fun `el método rechazado es un resultado de negocio`() {
        assertTrue(gateway.authorize(request("pm-test-rejected")) is GatewayAuthorizationResult.Rejected)
    }

    /** Verifica que el token configurado de error de infraestructura atraviese el límite de las excepciones técnicas. */
    @Test
    fun `el método de error es una excepción técnica`() {
        assertThrows(PaymentGatewayException::class.java) {
            gateway.authorize(request("pm-test-error"))
        }
    }

    /**
     * Crea una solicitud válida e independiente del proveedor variando solo el token del método que determina el escenario.
     *
     * @param method token simulado del método de pago que selecciona el comportamiento esperado.
     */
    private fun request(method: String) = PaymentGatewayRequest(
        OrderId("order-1"),
        OrderId("order-1"),
        Money.euros(BigDecimal("10.00")),
        PaymentMethodId(method),
    )
}
