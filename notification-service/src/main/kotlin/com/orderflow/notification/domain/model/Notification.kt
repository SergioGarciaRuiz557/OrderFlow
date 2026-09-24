package com.orderflow.notification.domain.model

/**
 * Identificador con tipado fuerte del pedido sobre el que se notifica al cliente.
 *
 * Un tipo específico evita que se pase por accidente una cadena con otro significado, como una
 * dirección de correo, donde se espera un identificador de pedido. [JvmInline] conserva esta
 * distinción en tiempo de compilación sin asignar normalmente un objeto envoltorio adicional en
 * tiempo de ejecución.
 *
 * @property value identificador externo del pedido incluido en el evento final de su ciclo de vida.
 * @throws IllegalArgumentException cuando [value] está vacío o solo contiene espacios en blanco.
 */
@JvmInline
value class OrderId(val value: String) {
    /** Aplica la única invariante del identificador de pedido que pertenece a Notificaciones. */
    init {
        // `require` rechaza de inmediato una entrada no válida en vez de permitir una notificación inutilizable.
        require(value.isNotBlank()) { "Order id must not be blank" }
    }
}

/**
 * Destinatario de correo electrónico de una notificación.
 *
 * La validación comprueba deliberadamente solo las condiciones que necesita este servicio. Una
 * validación completa según las RFC y específica del proveedor corresponderá a un futuro límite de
 * entrega. [JvmInline] aporta seguridad de tipos y conserva la eficiencia en ejecución de la cadena
 * envuelta en los puntos de llamada habituales.
 *
 * @property email dirección de correo de destino conservada exactamente como se recibió del comando
 * de aplicación.
 * @throws IllegalArgumentException cuando [email] no cumple el formato básico intencionado.
 */
@JvmInline
value class Recipient(val email: String) {
    /** Evita que datos de destinatario claramente inválidos lleguen a un adaptador de entrega. */
    init {
        // La expresión regular comprueba que haya texto a ambos lados de `@` y un dominio con punto.
        require(BASIC_EMAIL.matches(email)) { "Recipient must contain a valid email address" }
    }

    /** Implementación de validación compartida por cada construcción de [Recipient]. */
    private companion object {
        /**
         * Comprobación de correo deliberadamente reducida: sin espacios, un separador de dirección
         * y un dominio con punto. No pretende reproducir todas las RFC del correo ni las reglas
         * específicas de un proveedor.
         */
        val BASIC_EMAIL = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")
    }
}

/**
 * Conjunto cerrado de mensajes del ciclo de vida del pedido destinados al cliente que admite el servicio.
 *
 * El enum recorre el modelo [Notification], independiente del proveedor. Los adaptadores de entrega
 * pueden usarlo para registrar o mapear el proveedor sin recibir nombres de eventos de Kafka ni
 * otros conceptos de transporte.
 */
enum class NotificationType {
    /** El pedido completó correctamente el flujo de confirmación. */
    ORDER_CONFIRMED,

    /** El pedido alcanzó su estado final de cancelación. */
    ORDER_CANCELLED,
}

/**
 * Mensaje inmutable e independiente del proveedor, listo para entregarse a un cliente.
 *
 * Esta clase de datos no es un agregado de forma intencionada. El servicio no persiste
 * notificaciones ni gestiona un ciclo de vida de entrega, por lo que el modelo solo transporta la
 * información que necesita `NotificationSender`. La igualdad estructural que proporciona
 * `data class` también facilita verificar el contrato en las pruebas de aplicación.
 *
 * @property orderId pedido cuyo cambio final de ciclo de vida produjo la notificación.
 * @property recipient dirección de correo del cliente.
 * @property type tipo semántico de la notificación entregada.
 * @property message texto determinista destinado al cliente y construido por la capa de aplicación.
 * @throws IllegalArgumentException cuando [message] está vacío o solo contiene espacios en blanco.
 */
data class Notification(
    val orderId: OrderId,
    val recipient: Recipient,
    val type: NotificationType,
    val message: String,
) {
    /** Garantiza que toda notificación de salida contenga información útil para el cliente. */
    init {
        // Sería técnicamente posible enviar un mensaje vacío, pero no tendría una finalidad de negocio válida.
        require(message.isNotBlank()) { "Notification message must not be blank" }
    }
}
