package com.orderflow.payment.application.port.`out`

import com.orderflow.payment.domain.model.Money
import com.orderflow.payment.domain.model.OrderId
import com.orderflow.payment.domain.model.Payment
import com.orderflow.payment.domain.model.PaymentFailureReason
import com.orderflow.payment.domain.model.PaymentId
import com.orderflow.payment.domain.model.PaymentMethodId
import com.orderflow.payment.domain.model.PaymentProviderReference
import java.time.Instant

/**
 * Puerto de persistencia independiente del framework para agregados [Payment] completos.
 *
 * El código de la aplicación solo depende de este contrato. El repositorio de Spring Data y la entidad
 * JPA mutable siguen siendo detalles privados del adaptador de persistencia, lo que conserva la
 * dirección de dependencias hexagonal.
 */
interface PaymentRepository {
    /**
     * Recupera un agregado por su identidad técnica.
     *
     * @return pago almacenado o `null` cuando no existe.
     */
    fun findById(paymentId: PaymentId): Payment?

    /**
     * Recupera la operación de pago única asociada con un pedido.
     *
     * Esta búsqueda es la primera comprobación de idempotencia en el nivel de aplicación antes de invocar la pasarela.
     *
     * @return pago almacenado del pedido, o `null` cuando nunca se ha solicitado la autorización.
     */
    fun findByOrderId(orderId: OrderId): Payment?

    /**
     * Inserta o actualiza la instantánea completa del agregado.
     *
     * @param payment estado validado del dominio que se debe conservar.
     * @return instantánea conservada, incluida la versión actual de bloqueo optimista.
     * @throws DuplicatePaymentException cuando se infringe una regla de unicidad de la base de datos.
     */
    fun save(payment: Payment): Payment
}

/**
 * Solicitud de autorización independiente del proveedor enviada a través de [PaymentGateway].
 *
 * La clave de idempotencia de la pasarela usa intencionadamente [OrderId]. Permanece estable entre
 * reintentos del servicio e incluso al revertir una transacción local, lo que impide que un proveedor
 * que respete las claves de idempotencia cree un segundo cargo tras una respuesta incierta.
 *
 * @property idempotencyKey clave estable de la operación de negocio requerida por las implementaciones de la pasarela.
 * @property orderId pedido que se está pagando, incluido por separado como contexto de la solicitud de negocio.
 * @property amount importe y moneda que se deben autorizar.
 * @property paymentMethodId token opaco del método de pago del proveedor.
 */
data class PaymentGatewayRequest(
    val idempotencyKey: OrderId,
    val orderId: OrderId,
    val amount: Money,
    val paymentMethodId: PaymentMethodId,
)

/**
 * Conjunto exhaustivo de decisiones de negocio definitivas que puede devolver una pasarela.
 *
 * La incapacidad técnica de obtener una decisión se omite deliberadamente y debe representarse con
 * [PaymentGatewayException]. Esta separación controla el comportamiento futuro entre reintentar y
 * emitir un evento de rechazo.
 */
sealed interface GatewayAuthorizationResult {
    /**
     * Aprobación del proveedor.
     *
     * @property providerReference referencia rastreable que se debe almacenar con el agregado.
     */
    data class Authorized(val providerReference: PaymentProviderReference) : GatewayAuthorizationResult

    /**
     * Rechazo de negocio conocido del proveedor.
     *
     * @property reason motivo legible por máquina que pasará a formar parte del estado del agregado.
     */
    data class Rejected(val reason: PaymentFailureReason) : GatewayAuthorizationResult
}

/**
 * Puerto de salida que aísla la coordinación del pago de un proveedor externo concreto.
 *
 * Los adaptadores reales deben reenviar [PaymentGatewayRequest.idempotencyKey] mediante el mecanismo
 * de idempotencia nativo de su proveedor. Deben asignar los rechazos conocidos a
 * [GatewayAuthorizationResult.Rejected] y lanzar [PaymentGatewayException] cuando la decisión sea
 * desconocida a causa de un fallo de infraestructura.
 */
fun interface PaymentGateway {
    /**
     * Solicita la autorización sin exponer tipos del SDK del proveedor al código de aplicación o de dominio.
     *
     * @param request datos de autorización validados e independientes del proveedor.
     * @return autorización o rechazo de negocio definitivos.
     * @throws PaymentGatewayException ante tiempos de espera agotados, proveedores no disponibles,
     * respuestas mal formadas u otras condiciones técnicas sin un resultado de negocio fiable.
     */
    @Throws(PaymentGatewayException::class)
    fun authorize(request: PaymentGatewayRequest): GatewayAuthorizationResult
}

/**
 * Límite técnico para los fallos de comunicación con un proveedor de pagos.
 *
 * A diferencia de [GatewayAuthorizationResult.Rejected], esta excepción no cambia el estado del
 * agregado a `REJECTED`. La aplicación garantiza que se confirme un registro pendiente y después
 * propaga la excepción para que un futuro consumidor de mensajes pueda reintentarlo con la misma
 * clave de idempotencia.
 *
 * @param message resumen de diagnóstico seguro para los registros de la aplicación.
 * @param cause excepción subyacente del SDK del proveedor o del transporte, cuando esté disponible.
 */
class PaymentGatewayException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

/**
 * Pequeño puerto de salida para obtener marcas de tiempo autoritativas sin acoplar las pruebas de la aplicación al reloj del sistema.
 */
fun interface ClockProvider {
    /** @return instante UTC actual normalizado con la precisión que usa PostgreSQL. */
    fun now(): Instant
}

/**
 * Límite de exclusión mutua entre instancias para el flujo de autorización de un pedido.
 *
 * Toda la secuencia de lectura, creación, pasarela y escritura se ejecuta dentro de [operation].
 * Serializar valores [OrderId] iguales impide que dos solicitudes concurrentes superen la comprobación
 * inicial de ausencia y llamen al proveedor. Los pedidos diferentes pueden ejecutarse simultáneamente.
 */
interface PaymentAuthorizationLock {
    /**
     * Ejecuta [operation] mientras mantiene el bloqueo asociado a [orderId].
     *
     * @param orderId clave de bloqueo y clave de idempotencia de negocio del pago.
     * @param operation flujo de autorización protegido por el bloqueo que produce un valor no nulo.
     * @return resultado producido por [operation].
     */
    fun <T : Any> withLock(orderId: OrderId, operation: () -> T): T
}

/**
 * Traducción dirigida a la aplicación de una infracción de unicidad de PostgreSQL al guardar un pago.
 *
 * Mantener [org.springframework.dao.DataIntegrityViolationException] de Spring fuera del contrato
 * del puerto evita que el código central y las pruebas dependan de un framework de persistencia.
 *
 * @param cause excepción de infraestructura original conservada para el diagnóstico.
 */
class DuplicatePaymentException(cause: Throwable) : RuntimeException("A payment already exists for the order", cause)
