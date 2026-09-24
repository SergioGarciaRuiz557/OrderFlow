package com.orderflow.notification.application.service

import com.orderflow.notification.application.port.`in`.SendOrderCancelledNotificationCommand
import com.orderflow.notification.application.port.`in`.SendOrderCancelledNotificationUseCase
import com.orderflow.notification.application.port.`out`.NotificationSender
import org.springframework.stereotype.Service

/**
 * Servicio de aplicación que coordina el caso de uso de notificación de pedido cancelado.
 *
 * El servicio convierte un comando de entrada en una notificación completa mediante
 * [OrderNotificationFactory] y después delega la entrega en [NotificationSender]. Mantener aquí
 * estos pasos garantiza que un futuro listener de Kafka siga siendo un adaptador de entrada ligero
 * y que el mecanismo de correo permanezca aislado tras un puerto de salida.
 *
 * [Service] registra la implementación como bean de Spring para
 * [SendOrderCancelledNotificationUseCase]. La inyección por constructor documenta sus dependencias
 * y permite probar la clase directamente mediante pruebas unitarias.
 *
 * @property notificationFactory crea contenido de cancelación determinista.
 * @property notificationSender límite de entrega de salida implementado por un adaptador externo.
 */
@Service
class SendOrderCancelledNotificationService(
    private val notificationFactory: OrderNotificationFactory,
    private val notificationSender: NotificationSender,
) : SendOrderCancelledNotificationUseCase {
    /**
     * Ejecuta una solicitud de notificación de cancelación.
     *
     * No se almacena ningún estado de notificación porque el servicio carece de estado de forma
     * intencionada. Si la entrega falla, la excepción técnica del emisor se propaga sin cambios para
     * que el futuro adaptador de entrada asíncrono pueda aplicar su política de reintentos.
     *
     * @param command datos validados del pedido y destinatario procedentes del límite de entrada.
     * @throws com.orderflow.notification.application.port.out.NotificationDeliveryException cuando
     * el adaptador de salida informa de un fallo técnico de entrega.
     */
    override fun send(command: SendOrderCancelledNotificationCommand) {
        // Construye el mensaje estructurado de cancelación sin exponer la tecnología de entrega.
        val notification = notificationFactory.cancelled(command.orderId, command.recipient)

        // El puerto de salida selecciona en ejecución el adaptador configurado mediante inyección de dependencias.
        notificationSender.send(notification)
    }
}
