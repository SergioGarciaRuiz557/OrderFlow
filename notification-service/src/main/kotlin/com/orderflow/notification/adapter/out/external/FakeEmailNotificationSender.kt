package com.orderflow.notification.adapter.`out`.external

import com.orderflow.notification.application.port.`out`.NotificationSender
import com.orderflow.notification.domain.model.Notification
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * Adaptador de salida local que simula la entrega de correo sin infraestructura externa.
 *
 * El adaptador resulta útil para el desarrollo y la verificación de la arquitectura: recibe el
 * mismo modelo que recibiría una integración de correo real, pero registra la entrega en el log de
 * la aplicación en lugar de contactar con SMTP, SendGrid, SES u otro proveedor. No contiene ningún
 * comportamiento de construcción de mensajes.
 *
 * Sustituir este componente por un adaptador SMTP o de un proveedor de correo no afecta al código de
 * aplicación ni de dominio, porque ambos dependen únicamente de [NotificationSender]. [Component]
 * registra esta clase como implementación actual de Spring para ese puerto de salida.
 */
@Component
class FakeEmailNotificationSender : NotificationSender {
    /** Logger de SLF4J asociado a esta clase de adaptador y configurado por Spring Boot. */
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * Simula una entrega correcta de correo escribiendo en el log todos los campos relevantes.
     *
     * Se emplean marcadores de posición de SLF4J en lugar de concatenar cadenas para que el formato
     * solo se aplique cuando el nivel de log configurado habilite la sentencia. El método retorna con
     * normalidad después de registrar, lo que representa una entrega correcta en esta simulación
     * determinista.
     *
     * @param notification mensaje completo e independiente del proveedor creado por el servicio de aplicación.
     */
    override fun send(notification: Notification) {
        // Registra por separado los valores estructurados para que los recolectores analicen la traza de entrega.
        logger.info(
            // Cada marcador `{}` se rellena, en orden, con los cuatro argumentos siguientes.
            "Fake email delivered: recipient={}, type={}, orderId={}, message={}",
            // Primer marcador: dirección de correo de destino validada.
            notification.recipient.email,
            // Segundo marcador: tipo semántico de confirmación o cancelación.
            notification.type,
            // Tercer marcador: identificador de correlación del pedido.
            notification.orderId.value,
            // Cuarto marcador: texto determinista destinado al cliente.
            notification.message,
        )
    }
}
