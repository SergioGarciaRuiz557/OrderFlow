package com.orderflow.notification.application.port.`in`

import com.orderflow.notification.domain.model.OrderId
import com.orderflow.notification.domain.model.Recipient

/**
 * Solicitud independiente del framework y del transporte para notificar al cliente la confirmación del pedido.
 *
 * El adaptador de Kafka mapea en este comando los datos de `OrderConfirmedEvent`. Al mantener el
 * comando libre de clases de Kafka, el contrato de aplicación también puede invocarse desde pruebas
 * u otro adaptador de entrada sin modificar el caso de uso.
 *
 * @property orderId pedido confirmado que se mencionará en el mensaje.
 * @property recipient cliente que debe recibir el mensaje.
 */
data class SendOrderConfirmedNotificationCommand(
    val orderId: OrderId,
    val recipient: Recipient,
)

/**
 * Solicitud independiente del framework y del transporte para notificar al cliente la cancelación del pedido.
 *
 * El comando específico proporciona a la cancelación un vocabulario de aplicación explícito y
 * evita que el límite de entrada dependa de un indicador de acción genérico o de un tipo de evento
 * de transporte.
 *
 * @property orderId pedido cancelado que se mencionará en el mensaje.
 * @property recipient cliente que debe recibir el mensaje.
 */
data class SendOrderCancelledNotificationCommand(
    val orderId: OrderId,
    val recipient: Recipient,
)

/**
 * Puerto de entrada principal del flujo de notificación de pedido confirmado.
 *
 * La tecnología de entrada depende de esta interfaz y no del servicio de aplicación concreto.
 * Declararla como `fun interface` expresa que el límite tiene una sola operación y también permite
 * implementaciones lambda ligeras cuando resulten útiles.
 */
fun interface SendOrderConfirmedNotificationUseCase {
    /**
     * Construye y entrega la notificación de confirmación representada por [command].
     *
     * @param command valores validados del pedido y destinatario proporcionados por un adaptador de entrada.
     * @throws com.orderflow.notification.application.port.out.NotificationDeliveryException cuando
     * el mecanismo de entrega de salida seleccionado falla por un problema técnico.
     */
    fun send(command: SendOrderConfirmedNotificationCommand)
}

/**
 * Puerto de entrada principal del flujo de notificación de pedido cancelado.
 *
 * El puerto se mantiene independiente de Kafka para que un futuro listener solo tenga que
 * deserializar, mapear e invocar este contrato. El comportamiento de notificación permanece en la
 * capa de aplicación.
 */
fun interface SendOrderCancelledNotificationUseCase {
    /**
     * Construye y entrega la notificación de cancelación representada por [command].
     *
     * @param command valores validados del pedido y destinatario proporcionados por un adaptador de entrada.
     * @throws com.orderflow.notification.application.port.out.NotificationDeliveryException cuando
     * el mecanismo de entrega de salida seleccionado falla por un problema técnico.
     */
    fun send(command: SendOrderCancelledNotificationCommand)
}
