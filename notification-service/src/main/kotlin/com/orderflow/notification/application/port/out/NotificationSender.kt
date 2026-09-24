package com.orderflow.notification.application.port.`out`

import com.orderflow.notification.domain.model.Notification

/**
 * Puerto de salida independiente del proveedor para entregar una notificación a un cliente.
 *
 * Los servicios de aplicación deciden *cuándo* y *qué* notificar, pero desconocen *cómo* se produce
 * la entrega. Un adaptador proporciona esa implementación, actualmente mediante
 * `FakeEmailNotificationSender` y en el futuro quizá mediante SMTP o un proveedor externo de correo.
 * Esta dirección de dependencias mantiene la tecnología externa fuera del núcleo de la aplicación.
 *
 * Su única operación permite usar una `fun interface`, lo que mantiene el contrato centrado y
 * permite dobles de prueba concisos o adaptadores alternativos.
 */
fun interface NotificationSender {
    /**
     * Entrega [notification].
     *
     * Las implementaciones lanzan [NotificationDeliveryException] cuando la entrega falla por un
     * problema técnico. La aplicación propaga el fallo de forma intencionada para que un futuro
     * consumidor de mensajes pueda reintentarlo.
     *
     * @param notification mensaje completo e independiente del proveedor que se debe entregar.
     * @throws NotificationDeliveryException cuando el adaptador no puede completar la entrega por
     * un motivo técnico, como la falta de disponibilidad del proveedor.
     */
    fun send(notification: Notification)
}

/**
 * Fallo técnico de entrega notificado por un adaptador [NotificationSender].
 *
 * Esta excepción pertenece al contexto de notificaciones y se distingue deliberadamente de los
 * resultados de negocio de los pedidos. Los servicios de aplicación permiten que se propague para
 * que un futuro consumidor asíncrono pueda reintentar o dirigir el evento a un mecanismo de mensajes
 * fallidos, en lugar de tratar una caída del proveedor como una cancelación del pedido.
 *
 * @param message resumen de diagnóstico del fallo legible para personas.
 * @param cause excepción original opcional del proveedor o de red conservada para el diagnóstico.
 */
class NotificationDeliveryException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
