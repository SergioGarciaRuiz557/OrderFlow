package com.orderflow.notification.application.service

import com.orderflow.notification.application.port.`in`.SendOrderConfirmedNotificationCommand
import com.orderflow.notification.application.port.`in`.SendOrderConfirmedNotificationUseCase
import com.orderflow.notification.application.port.`out`.NotificationSender
import org.springframework.stereotype.Service

/**
 * Servicio de aplicación que coordina el caso de uso de notificación de pedido confirmado.
 *
 * El servicio realiza exactamente dos pasos en el nivel de aplicación: pide a
 * [OrderNotificationFactory] que cree contenido independiente del proveedor y pasa ese contenido a
 * [NotificationSender]. No contiene deserialización de Kafka, llamadas a una API de correo,
 * registro ni lógica de persistencia.
 *
 * [Service] pone esta implementación a disposición de Spring como bean concreto de
 * [SendOrderConfirmedNotificationUseCase]. La inyección por constructor hace explícitas ambas
 * dependencias y permite que las pruebas unitarias proporcionen un emisor simulado sin iniciar Spring.
 *
 * @property notificationFactory crea contenido de confirmación determinista.
 * @property notificationSender límite de entrega de salida implementado por un adaptador externo.
 */
@Service
class SendOrderConfirmedNotificationService(
    private val notificationFactory: OrderNotificationFactory,
    private val notificationSender: NotificationSender,
) : SendOrderConfirmedNotificationUseCase {
    /**
     * Ejecuta una solicitud de notificación de confirmación.
     *
     * Un retorno correcto significa que el emisor configurado terminó con normalidad. Las
     * excepciones de entrega no se capturan ni convierten aquí de forma intencionada; conservar el
     * fallo técnico permite que un futuro consumidor de Kafka controle los reintentos y las
     * confirmaciones.
     *
     * @param command datos validados del pedido y destinatario procedentes del límite de entrada.
     * @throws com.orderflow.notification.application.port.out.NotificationDeliveryException cuando
     * el adaptador de salida informa de un fallo técnico de entrega.
     */
    override fun send(command: SendOrderConfirmedNotificationCommand) {
        // Traduce el comando del caso de uso al modelo completo que necesita el puerto de salida.
        val notification = notificationFactory.confirmed(command.orderId, command.recipient)

        // Delega la entrega mediante el puerto; este servicio desconoce el adaptador de correo simulado.
        notificationSender.send(notification)
    }
}
