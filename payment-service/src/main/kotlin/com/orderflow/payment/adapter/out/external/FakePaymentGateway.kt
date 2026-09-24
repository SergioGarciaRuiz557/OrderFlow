package com.orderflow.payment.adapter.`out`.external

import com.orderflow.payment.application.port.`out`.GatewayAuthorizationResult
import com.orderflow.payment.application.port.`out`.PaymentGateway
import com.orderflow.payment.application.port.`out`.PaymentGatewayException
import com.orderflow.payment.application.port.`out`.PaymentGatewayRequest
import com.orderflow.payment.domain.model.PaymentFailureReason
import com.orderflow.payment.domain.model.PaymentProviderReference
import org.springframework.stereotype.Component

/**
 * Adaptador determinista de desarrollo y pruebas para el puerto de salida [PaymentGateway].
 *
 * Este componente simula un proveedor sin acceso a la red, secretos, aleatoriedad ni comportamiento
 * basado en el tiempo. Su resultado solo depende de [PaymentGatewayRequest.paymentMethodId], lo que
 * hace reproducibles las ejecuciones locales y las pruebas automatizadas. Demuestra el límite
 * semántico requerido: un rechazo conocido del proveedor se devuelve como resultado de negocio,
 * mientras que un proveedor no disponible o con fallos lanza una [PaymentGatewayException] técnica.
 *
 * Este adaptador no es un endpoint REST ni define el futuro mecanismo de integración de entrada.
 * Sustituirlo por un proveedor real requiere otro adaptador que implemente el mismo contrato del
 * puerto; el código de dominio y de aplicación no cambia.
 */
@Component
class FakePaymentGateway : PaymentGateway {
    /**
     * Simula la autorización del proveedor según tokens conocidos de métodos de pago de desarrollo.
     *
     * - `pm-test-success` devuelve una referencia de autorización derivada de la clave de idempotencia estable.
     * - `pm-test-rejected` devuelve un rechazo de negocio definitivo.
     * - `pm-test-error` lanza una excepción técnica y, por tanto, deja el pago disponible para reintentos.
     * - Cualquier otro token se trata como un rechazo de negocio conocido por método no admitido.
     *
     * @param request solicitud independiente del proveedor creada por el servicio de aplicación.
     * @return resultado de negocio determinista satisfactorio o rechazado.
     * @throws PaymentGatewayException solo para el token de prueba explícito de error técnico.
     */
    override fun authorize(request: PaymentGatewayRequest): GatewayAuthorizationResult =
        // `when` es exhaustivo para los escenarios previstos de la simulación y no contiene ninguna alternativa aleatoria.
        when (request.paymentMethodId.value) {
            "pm-test-success" -> GatewayAuthorizationResult.Authorized(
                // La clave estable del pedido hace que las respuestas simuladas repetidas sean idénticas.
                PaymentProviderReference("fake-${request.idempotencyKey.value}"),
            )
            "pm-test-rejected" -> GatewayAuthorizationResult.Rejected(
                PaymentFailureReason("PAYMENT_METHOD_REJECTED"),
            )
            "pm-test-error" -> throw PaymentGatewayException("Fake payment gateway technical failure")
            else -> GatewayAuthorizationResult.Rejected(PaymentFailureReason("UNSUPPORTED_PAYMENT_METHOD"))
        }
}
