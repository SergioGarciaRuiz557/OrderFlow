package com.orderflow.notification.application.service

import com.orderflow.notification.domain.model.Notification
import com.orderflow.notification.domain.model.NotificationType
import com.orderflow.notification.domain.model.OrderId
import com.orderflow.notification.domain.model.Recipient
import org.springframework.stereotype.Component

/**
 * Crea notificaciones completas de pedidos y su contenido determinista destinado al cliente.
 *
 * Centralizar la construcción de mensajes evita que ambos servicios de aplicación incluyan texto y
 * proporciona un punto claro de sustitución para una futura implementación basada en plantillas.
 * Esta clase se mantiene pequeña de forma intencionada: la localización, los motores de plantillas y
 * los proveedores remotos de contenido no son requisitos actuales.
 *
 * [Component] registra la factoría en el contexto de aplicación de Spring para que pueda inyectarse
 * mediante el constructor en ambos servicios de notificaciones. Su API pública permanece
 * independiente de Spring.
 */
@Component
class OrderNotificationFactory {
    /**
     * Construye el mensaje que se envía después de confirmar un pedido.
     *
     * @param orderId pedido confirmado incluido en el texto destinado al cliente.
     * @param recipient destino que recibirá la notificación.
     * @return notificación de confirmación inmutable y lista para el puerto de salida.
     */
    fun confirmed(orderId: OrderId, recipient: Recipient): Notification = Notification(
        // Conserva el identificador de negocio validado que se recibió en el límite de entrada.
        orderId = orderId,
        // Conserva el destinatario validado; los adaptadores de entrega no reinterpretan los datos de aplicación.
        recipient = recipient,
        // El tipo explícito permite que los adaptadores de salida distingan el mensaje sin analizar el texto.
        type = NotificationType.ORDER_CONFIRMED,
        // La interpolación produce contenido estable y evita introducir antes de tiempo un motor de plantillas.
        message = "Your order ${orderId.value} has been confirmed.",
    )

    /**
     * Construye el mensaje que se envía después de cancelar un pedido.
     *
     * @param orderId pedido cancelado incluido en el texto destinado al cliente.
     * @param recipient destino que recibirá la notificación.
     * @return notificación de cancelación inmutable y lista para el puerto de salida.
     */
    fun cancelled(orderId: OrderId, recipient: Recipient): Notification = Notification(
        // El mismo identificador de pedido se transporta como dato estructurado y se muestra en el mensaje.
        orderId = orderId,
        // El destinatario permanece independiente del proveedor hasta que lo recibe el adaptador de salida.
        recipient = recipient,
        // La cancelación se modela por separado de la confirmación dentro del conjunto de tipos admitidos.
        type = NotificationType.ORDER_CANCELLED,
        // La redacción determinista hace que el comportamiento actual sea predecible y fácil de probar.
        message = "Your order ${orderId.value} has been cancelled.",
    )
}
