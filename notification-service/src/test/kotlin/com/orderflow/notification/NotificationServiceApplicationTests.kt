package com.orderflow.notification

import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest

/**
 * Prueba mínima de integración con Spring para el contexto completo del servicio de notificaciones.
 *
 * Las pruebas unitarias instancian directamente los servicios de aplicación y validan su
 * comportamiento de forma aislada. Esta prueba complementaria pide a Spring Boot que descubra y
 * construya el grafo real de componentes, con lo que demuestra que los servicios de aplicación, la
 * factoría de notificaciones y el emisor simulado pueden conectarse con la configuración de producción.
 *
 * [SpringBootTest] inicia el contexto de aplicación sin necesitar un endpoint REST, un broker de
 * Kafka, una base de datos ni un proveedor externo de correo.
 */
@SpringBootTest(properties = ["orderflow.kafka.enabled=false"])
class NotificationServiceApplicationTests {

    /**
     * Verifica que Spring pueda iniciar el servicio con todos los beans y la configuración necesarios.
     *
     * El cuerpo está vacío de forma intencionada: JUnit solo llega a él después de que
     * [SpringBootTest] haya completado la inicialización del contexto. Un bean ausente, una
     * implementación en conflicto o una configuración no válida hacen fallar la prueba antes de que
     * retorne este método.
     */
    @Test
    fun contextLoads() {
        // Entrar en este método y retornar correctamente significa que la creación del contexto funcionó.
    }
}
