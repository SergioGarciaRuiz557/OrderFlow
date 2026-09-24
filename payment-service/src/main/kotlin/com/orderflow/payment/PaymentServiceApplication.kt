package com.orderflow.payment

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

/**
 * Raíz de composición de Spring Boot para el microservicio de Pagos.
 *
 * [SpringBootApplication] habilita la configuración automática y el análisis de componentes desde
 * el paquete `com.orderflow.payment`. Descubre los servicios de aplicación, los adaptadores de
 * salida, los repositorios JPA y [com.orderflow.payment.configuration.PaymentConfiguration], y los
 * conecta mediante sus interfaces de puertos de entrada y salida. Esta clase de arranque no contiene
 * comportamiento de negocio.
 */
@SpringBootApplication
class PaymentServiceApplication

/**
 * Punto de entrada de la JVM que utiliza el artefacto ejecutable de Spring Boot.
 *
 * @param args argumentos de línea de comandos que se reenvían sin cambios a Spring Boot, lo que
 * permite sustituir propiedades estándar como los perfiles y las ubicaciones de configuración.
 */
fun main(args: Array<String>) {
    // El inicio con un genérico reificado identifica PaymentServiceApplication como clase de configuración principal.
    runApplication<PaymentServiceApplication>(*args)
}
