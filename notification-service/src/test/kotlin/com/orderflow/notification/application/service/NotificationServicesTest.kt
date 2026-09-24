package com.orderflow.notification.application.service

import com.orderflow.notification.application.port.`in`.SendOrderCancelledNotificationCommand
import com.orderflow.notification.application.port.`in`.SendOrderConfirmedNotificationCommand
import com.orderflow.notification.application.port.`out`.NotificationDeliveryException
import com.orderflow.notification.application.port.`out`.NotificationSender
import com.orderflow.notification.domain.model.Notification
import com.orderflow.notification.domain.model.NotificationType
import com.orderflow.notification.domain.model.OrderId
import com.orderflow.notification.domain.model.Recipient
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

/**
 * Pruebas unitarias de la orquestación de los servicios de aplicación de confirmación y cancelación.
 *
 * Estas pruebas no cargan Spring. La implementación real de [OrderNotificationFactory] verifica la
 * construcción determinista actual del contenido, mientras que un [NotificationSender] de MockK
 * aísla la entrega externa. Así, los escenarios se centran en el contrato de aplicación: construir
 * la notificación correcta, llamar una vez al puerto de salida, conservar el destinatario y
 * propagar los fallos técnicos.
 */
class NotificationServicesTest {
    /** Puerto de salida simulado para observar intentos de entrega sin generar efectos de log ni de red. */
    private val notificationSender = mockk<NotificationSender>()

    /** Factoría real sin estado para que las pruebas cubran el contenido exacto del mensaje de producción. */
    private val notificationFactory = OrderNotificationFactory()

    /**
     * Verifica el flujo completo de confirmación correcta y el modelo que genera.
     *
     * El conjunto de aserciones protege el tipo semántico, la correlación del pedido y el texto
     * determinista exacto, mientras MockK verifica que la entrega se solicite exactamente una vez.
     */
    @Test
    fun `debe enviar la notificación de confirmación`() {
        // Preparación: configura el emisor simulado para representar una entrega correcta que devuelve Unit.
        every { notificationSender.send(any()) } returns Unit

        // Preparación: el slot captura la notificación real que se pasa por el puerto de salida.
        val notification = slot<Notification>()

        // Preparación: instancia directamente el servicio de aplicación con dependencias de producción y prueba.
        val service = SendOrderConfirmedNotificationService(notificationFactory, notificationSender)

        // Acción: ejecuta la operación pública del puerto de entrada con valores de comando validados.
        service.send(
            SendOrderConfirmedNotificationCommand(
                // El identificador del pedido debe conservarse estructuralmente e interpolarse en el mensaje.
                orderId = OrderId("order-123"),
                // Un destinatario de correo válido satisface la validación ligera del dominio.
                recipient = Recipient("customer@example.com"),
            ),
        )

        // Verificación: se produjo una entrega y se captura su argumento para comprobar sus campos.
        verify(exactly = 1) { notificationSender.send(capture(notification)) }

        // Verificación: la factoría clasificó el mensaje como una confirmación de pedido.
        assertEquals(NotificationType.ORDER_CONFIRMED, notification.captured.type)

        // Verificación: el modelo de salida conserva el identificador de pedido del comando de entrada.
        assertEquals(OrderId("order-123"), notification.captured.orderId)

        // Verificación: el texto del mensaje es determinista y contiene el identificador de pedido correcto.
        assertEquals("Your order order-123 has been confirmed.", notification.captured.message)
    }

    /**
     * Verifica que el flujo correcto de cancelación produzca el tipo y contenido propios de la cancelación.
     *
     * Este escenario separado evita que el texto o la clasificación de confirmación se reutilicen
     * por accidente para un `OrderCancelledEvent`.
     */
    @Test
    fun `debe enviar la notificación de cancelación`() {
        // Preparación: un retorno normal del emisor simulado representa una entrega correcta.
        every { notificationSender.send(any()) } returns Unit

        // Preparación: captura el modelo exacto que envía el servicio de cancelación.
        val notification = slot<Notification>()

        // Preparación: construye la unidad bajo prueba sin iniciar el contenedor de Spring.
        val service = SendOrderCancelledNotificationService(notificationFactory, notificationSender)

        // Acción: envía un comando de cancelación mediante la implementación del puerto de entrada del servicio.
        service.send(
            SendOrderCancelledNotificationCommand(
                orderId = OrderId("order-456"),
                recipient = Recipient("customer@example.com"),
            ),
        )

        // Verificación: el puerto de salida se llama una vez y su argumento queda disponible en el slot.
        verify(exactly = 1) { notificationSender.send(capture(notification)) }

        // Verificación: la cancelación se mantiene semánticamente diferenciada de la confirmación.
        assertEquals(NotificationType.ORDER_CANCELLED, notification.captured.type)

        // Verificación: la aplicación conserva el identificador del pedido cancelado.
        assertEquals(OrderId("order-456"), notification.captured.orderId)

        // Verificación: el texto determinista de cancelación contiene el identificador y estado correctos.
        assertEquals("Your order order-456 has been cancelled.", notification.captured.message)
    }

    /**
     * Verifica que se conserve el cliente previsto durante el mapeo de comando a notificación.
     *
     * Se comprueba explícitamente que el destinatario sea correcto porque entregar contenido válido
     * en una dirección equivocada sería un fallo crítico de la aplicación, aunque los demás campos
     * fueran correctos.
     */
    @Test
    fun `debe invocar el emisor de notificaciones con el destinatario esperado`() {
        // Preparación: permite que la simulación envíe correctamente cualquier notificación.
        every { notificationSender.send(any()) } returns Unit

        // Preparación: conserva una instancia tipada del destinatario como destino esperado.
        val expectedRecipient = Recipient("expected@example.com")

        // Preparación: usa el flujo de confirmación; ambos flujos comparten el mapeo del destinatario.
        val service = SendOrderConfirmedNotificationService(notificationFactory, notificationSender)

        // Acción: solicita la entrega en la dirección esperada.
        service.send(SendOrderConfirmedNotificationCommand(OrderId("order-789"), expectedRecipient))

        // Verificación: exactamente una llamada de salida contiene ese mismo valor tipado de destinatario.
        verify(exactly = 1) {
            notificationSender.send(match { it.recipient == expectedRecipient })
        }
    }

    /**
     * Verifica que una caída del emisor siga siendo un fallo técnico específico de notificaciones.
     *
     * La aplicación no debe silenciar la excepción ni convertirla en un resultado del dominio de
     * pedidos. La comparación exacta de la instancia demuestra que el fallo original se propaga sin
     * cambios y conserva su mensaje, causa y futuro contexto de diagnóstico para un mecanismo de
     * reintento asíncrono.
     */
    @Test
    fun `debe propagar el fallo técnico del emisor`() {
        // Preparación: crea el fallo exacto que notificará el adaptador de salida simulado.
        val failure = NotificationDeliveryException("provider unavailable")

        // Preparación: configura cada intento de entrega para que lance una excepción en vez de retornar.
        every { notificationSender.send(any()) } throws failure

        // Preparación: la cancelación basta para verificar la política de propagación compartida.
        val service = SendOrderCancelledNotificationService(notificationFactory, notificationSender)

        // Acción y verificación: ejecuta el caso de uso y captura la excepción técnica esperada.
        val thrown = assertThrows(NotificationDeliveryException::class.java) {
            service.send(
                SendOrderCancelledNotificationCommand(
                    // Un pedido válido garantiza que el escenario alcance el emisor sin fallar en la validación.
                    OrderId("order-technical-failure"),
                    // Un destinatario válido mantiene también la prueba centrada en el fallo de salida.
                    Recipient("customer@example.com"),
                ),
            )
        }

        // Verificación: la aplicación relanza el fallo original del adaptador sin envolverlo ni sustituirlo.
        assertSame(failure, thrown)

        // Verificación: se intentó exactamente una entrega antes de que se propagara el fallo técnico.
        verify(exactly = 1) { notificationSender.send(any()) }
    }
}
