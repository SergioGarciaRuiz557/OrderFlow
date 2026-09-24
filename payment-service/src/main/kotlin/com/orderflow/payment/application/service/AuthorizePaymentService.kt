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

/**
 * Servicio de aplicación que coordina el caso de uso completo de autorización de pagos.
 *
 * El servicio contiene coordinación, no detalles del proveedor ni de persistencia. Serializa el
 * trabajo del pedido, localiza o crea el agregado, delega la decisión externa en [PaymentGateway],
 * invoca el comportamiento de [Payment] y conserva la instantánea resultante. Los invariantes de
 * negocio permanecen en el agregado, mientras que las decisiones de idempotencia que requieren
 * acceso al repositorio pertenecen aquí.
 *
 * Los fallos técnicos de la pasarela reciben un tratamiento transaccional especial: el servicio
 * convierte la excepción lanzada en un valor interno temporal mientras se encuentra dentro de
 * [PaymentAuthorizationLock]. Así, el adaptador del bloqueo puede confirmar el pago `PENDING` recién
 * creado. Inmediatamente después de esa confirmación se vuelve a lanzar la misma excepción al
 * llamador. El resultado sigue siendo reintentable sin disfrazar un fallo de infraestructura como
 * rechazo de negocio.
 *
 * @property repository acceso dirigido al dominio a los agregados de pago conservados.
 * @property gateway límite de autorización de salida independiente del proveedor.
 * @property clock fuente determinista de marcas de tiempo de creación y transición.
 * @property authorizationLock serialización entre instancias para el mismo pedido.
 */
@Service
class AuthorizePaymentService(
    private val repository: PaymentRepository,
    private val gateway: PaymentGateway,
    private val clock: ClockProvider,
    private val authorizationLock: PaymentAuthorizationLock,
) : AuthorizePaymentUseCase {

    /**
     * Procesa una autorización nueva o devuelve un resultado definitivo conservado previamente.
     *
     * El bloqueo abarca las lecturas del repositorio, la llamada a la pasarela y las escrituras del
     * repositorio. Es intencionado: sin él, dos comandos simultáneos para un pedido hasta entonces
     * desconocido podrían llamar ambos al proveedor externo antes de que la restricción de unicidad
     * de la base de datos rechazara una de las inserciones locales.
     *
     * [PaymentGatewayException] solo se captura dentro de la función de retorno de la transacción.
     * Devolver el marcador [AuthorizationExecution.TechnicalFailure] hace que esta termine con
     * normalidad y, por tanto, confirme `PENDING`; el `when` exhaustivo externo restablece después el
     * contrato público de la excepción, una vez finalizada la transacción del bloqueo.
     *
     * @param command datos validados del pedido, el importe y el método de pago.
     * @return un resultado de negocio definitivo, autorizado o rechazado.
     * @throws PaymentGatewayException después de confirmar el estado pendiente de reintento.
     * @throws PaymentRequestConflictException si se repite un pedido con datos modificados en la solicitud.
     */
    override fun authorize(command: AuthorizePaymentCommand): PaymentAuthorizationResult =
        // PostgreSQL usa command.orderId para serializar únicamente el trabajo que compite por este pago de negocio.
        when (val execution = authorizationLock.withLock(command.orderId) {
            try {
                AuthorizationExecution.Completed(authorizeWithinLock(command))
            } catch (exception: PaymentGatewayException) {
                // Una devolución normal de la función confirma PENDING; más abajo se vuelve a lanzar la excepción fuera de ella.
                AuthorizationExecution.TechnicalFailure(exception)
            }
        }) {
            // Sellar el resultado interno evita que se olvide aquí una futura rama.
            is AuthorizationExecution.Completed -> execution.result
            is AuthorizationExecution.TechnicalFailure -> throw execution.exception
        }

    /**
     * Toma todas las decisiones dependientes del estado mientras mantiene el bloqueo circunscrito al pedido.
     *
     * El orden de evaluación es relevante:
     * 1. Se carga el estado existente mediante la clave de negocio del pedido.
     * 2. Un comando repetido debe tener el importe y el método originales.
     * 3. Un pago ausente se crea y se vacía como pendiente antes de llamar a la pasarela.
     * 4. Los pagos terminales regresan de forma idempotente; solo los pendientes llegan al proveedor.
     *
     * @param command solicitud de autorización protegida actualmente por el bloqueo del pedido.
     * @return resultado de negocio definitivo, salvo que la pasarela lance una excepción técnica.
     */
    private fun authorizeWithinLock(command: AuthorizePaymentCommand): PaymentAuthorizationResult {
        // `findByOrderId` es la comprobación de idempotencia de la aplicación previa a cualquier llamada externa.
        val payment = repository.findByOrderId(command.orderId)
            ?.also { existing ->
                // El mismo pedido con una intención inmutable diferente es un conflicto, no un reintento idempotente.
                if (!existing.matches(command.amount, command.paymentMethodId)) {
                    throw PaymentRequestConflictException(command.orderId.value)
                }
            }
            // La rama Elvis solo se ejecuta para la primera solicitud de autorización de este pedido.
            ?: repository.save(
                Payment.pending(
                    id = PaymentId.new(),
                    orderId = command.orderId,
                    amount = command.amount,
                    paymentMethodId = command.paymentMethodId,
                    createdAt = clock.now(),
                ),
            )

        // Los resultados terminales nunca vuelven a llamar al proveedor ni a escribir; pendiente es el único estado activo.
        return when (payment.status) {
            PaymentStatus.AUTHORIZED -> PaymentAuthorizationResult.Authorized(payment, alreadyProcessed = true)
            PaymentStatus.REJECTED -> PaymentAuthorizationResult.Rejected(
                payment,
                // Los invariantes del agregado garantizan que un pago rechazado siempre contenga este valor.
                requireNotNull(payment.failureReason),
                alreadyProcessed = true,
            )
            PaymentStatus.PENDING -> authorizePending(payment)
        }
    }

    /**
     * Llama al proveedor para un agregado pendiente y conserva su transición definitiva de dominio.
     *
     * Se usa [OrderId] como clave de idempotencia del proveedor. Aunque el proveedor responda
     * satisfactoriamente y después falle la persistencia local, repetir el comando usa la misma clave
     * y no debe crear otro cargo en una implementación conforme de la pasarela.
     *
     * @param payment agregado pendiente conservado cuyos datos inmutables de solicitud se envían a la pasarela.
     * @return resultado de aplicación autorizado o rechazado y conservado, con `alreadyProcessed = false`.
     * @throws PaymentGatewayException cuando el proveedor no devuelve una decisión definitiva.
     */
    private fun authorizePending(payment: Payment): PaymentAuthorizationResult {
        // Este DTO es la única representación visible para un adaptador de proveedor externo.
        val request = PaymentGatewayRequest(
            idempotencyKey = payment.orderId,
            orderId = payment.orderId,
            amount = payment.amount,
            paymentMethodId = payment.paymentMethodId,
        )

        // El resultado sellado mantiene explícitos y exhaustivos los resultados normales de negocio.
        return when (val outcome = gateway.authorize(request)) {
            is GatewayAuthorizationResult.Authorized -> {
                // El comportamiento del dominio valida la transición antes de que la nueva instantánea llegue a JPA.
                val saved = repository.save(payment.authorize(outcome.providerReference, clock.now()))
                PaymentAuthorizationResult.Authorized(saved, alreadyProcessed = false)
            }
            is GatewayAuthorizationResult.Rejected -> {
                // Se conserva un rechazo conocido; esta rama nunca se usa para excepciones técnicas.
                val saved = repository.save(payment.reject(outcome.reason, clock.now()))
                PaymentAuthorizationResult.Rejected(saved, outcome.reason, alreadyProcessed = false)
            }
        }
    }

    /**
     * Resultado interno de control de la transacción que solo se usa para retrasar la propagación de una excepción técnica.
     *
     * No forma parte del contrato público del caso de uso y nunca convierte un fallo técnico en un
     * resultado de negocio. Su único propósito es permitir que la función del bloqueo regrese con
     * normalidad para confirmar el estado pendiente antes de volver a lanzar [TechnicalFailure.exception].
     */
    private sealed interface AuthorizationExecution {
        /** Finalización normal de la función que transporta el resultado público del caso de uso. */
        data class Completed(val result: PaymentAuthorizationResult) : AuthorizationExecution

        /** Fallo técnico diferido que debe volver a lanzarse inmediatamente después de confirmar la transacción. */
        data class TechnicalFailure(val exception: PaymentGatewayException) : AuthorizationExecution
    }
}

/**
 * Señala la reutilización de un identificador de pedido con un importe o método de pago diferentes.
 *
 * Tratar silenciosamente una intención modificada como reintento devolvería un resultado histórico
 * erróneo o entrañaría el riesgo de cobrar datos inesperados. Por ello, la aplicación se detiene antes
 * de llamar a la pasarela.
 *
 * @param orderId valor externo del pedido incluido en el mensaje de diagnóstico.
 */
class PaymentRequestConflictException(orderId: String) :
    RuntimeException("Order $orderId already has a different payment request")
