package com.orderflow.notification

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

/**
 * Configuración del punto de entrada de Spring Boot para el servicio de notificaciones.
 *
 * Esta clase no contiene comportamiento de negocio. Su única responsabilidad es definir la raíz
 * del contexto de aplicación de Spring. Como se encuentra en el paquete raíz
 * `com.orderflow.notification`, el escaneo de componentes descubre los servicios de aplicación y
 * el adaptador de salida simulado situados en sus subpaquetes.
 *
 * [SpringBootApplication] combina las capacidades de configuración, escaneo de componentes y
 * autoconfiguración de Spring. Mantener aquí esa responsabilidad del framework evita que el modelo
 * de dominio y los puertos de aplicación dependan de Spring Boot.
 */
@SpringBootApplication
class NotificationServiceApplication

/**
 * Inicia el servicio de notificaciones como un proceso ejecutable independiente.
 *
 * [runApplication] crea el contexto de aplicación de Spring, aplica la configuración de
 * `application.yml`, descubre los beans del servicio y mantiene el proceso en ejecución. El
 * operador de expansión (`*`) reenvía a Spring Boot todos los argumentos de línea de comandos que
 * recibe este proceso.
 *
 * @param args argumentos opcionales de línea de comandos proporcionados al proceso de la aplicación.
 */
fun main(args: Array<String>) {
    runApplication<NotificationServiceApplication>(*args)
}
