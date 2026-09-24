package com.orderflow.payment.application.port.`in`

import com.orderflow.payment.domain.model.Money
import com.orderflow.payment.domain.model.OrderId
import com.orderflow.payment.domain.model.Payment
import com.orderflow.payment.domain.model.PaymentFailureReason
import com.orderflow.payment.domain.model.PaymentId
import com.orderflow.payment.domain.model.PaymentMethodId

/**
 * Entrada independiente del framework necesaria para autorizar el pago de un pedido.
 *
 * El adaptador de Kafka deserializa los datos de transporte y construye este comando antes de invocar
 * [AuthorizePaymentUseCase]. Usar objetos de valor del dominio en el límite del puerto de entrada
 * garantiza que se rechacen los identificadores en blanco, los valores monetarios no admitidos y las
 * escalas no válidas antes de comenzar la coordinación.
 *
 * @property orderId pedido y operación de pago de negocio que se deben autorizar.
 * @property amount valor monetario explícito esperado por el pedido.
 * @property paymentMethodId token del proveedor que identifica el instrumento de pago del cliente.
 */
data class AuthorizePaymentCommand(
    val orderId: OrderId,
    val amount: Money,
    val paymentMethodId: PaymentMethodId,
)

/**
 * Resultado de negocio exhaustivo que devuelve la autorización del pago.
 *
 * Aquí solo aparecen decisiones definitivas del proveedor. Los fallos de infraestructura se propagan
 * como [com.orderflow.payment.application.port.out.PaymentGatewayException], porque el llamador debe
 * reintentarlos en lugar de publicar un rechazo. La jerarquía sellada obliga a cada llamador a tratar
 * explícitamente la autorización y el rechazo mediante una expresión `when` exhaustiva.
 */
sealed interface PaymentAuthorizationResult {
    /** Instantánea del agregado que contiene el resultado definitivo. */
    val payment: Payment

    /**
     * Indica si la respuesta procede de una autorización anterior ya completada.
     *
     * `true` significa que no se llamó a la pasarela ni se volvió a escribir la base de datos durante esta solicitud.
     */
    val alreadyProcessed: Boolean

    /**
     * Resultado de negocio satisfactorio.
     *
     * @property payment agregado autorizado que contiene su referencia del proveedor.
     * @property alreadyProcessed `true` para una repetición idempotente de un resultado satisfactorio anterior.
     */
    data class Authorized(
        override val payment: Payment,
        override val alreadyProcessed: Boolean,
    ) : PaymentAuthorizationResult

    /**
     * Rechazo de negocio definitivo, como el de un instrumento de pago rechazado.
     *
     * @property payment agregado rechazado que contiene el mismo motivo.
     * @property reason motivo de negocio estable apto para un futuro evento de rechazo.
     * @property alreadyProcessed `true` para una repetición idempotente del rechazo original.
     */
    data class Rejected(
        override val payment: Payment,
        val reason: PaymentFailureReason,
        override val alreadyProcessed: Boolean,
    ) : PaymentAuthorizationResult
}

/**
 * Puerto de entrada principal para procesar una autorización de pago.
 *
 * Las tecnologías de entrada dependen de esta interfaz en lugar de
 * [com.orderflow.payment.application.service.AuthorizePaymentService], lo que permite sustituir
 * Kafka, las pruebas u otro adaptador futuro sin modificar el flujo de negocio.
 */
fun interface AuthorizePaymentUseCase {
    /**
     * Autoriza un pago nuevo o devuelve el resultado almacenado para un duplicado exacto.
     *
     * @param command solicitud de negocio validada.
     * @return resultado de negocio definitivo, autorizado o rechazado.
     * @throws com.orderflow.payment.application.service.PaymentRequestConflictException cuando el
     * pedido ya tiene un pago con datos inmutables de solicitud diferentes.
     * @throws com.orderflow.payment.application.port.out.PaymentGatewayException cuando no se puede
     * obtener una decisión definitiva del proveedor.
     */
    fun authorize(command: AuthorizePaymentCommand): PaymentAuthorizationResult
}

/** Puerto de entrada de solo lectura para recuperar la instantánea actual de un agregado de pago. */
fun interface GetPaymentUseCase {
    /**
     * Busca un pago por el identificador de su agregado.
     *
     * @param paymentId identificador asignado al crear el pago.
     * @return pago conservado o `null` cuando el identificador es desconocido.
     */
    fun getPayment(paymentId: PaymentId): Payment?
}
