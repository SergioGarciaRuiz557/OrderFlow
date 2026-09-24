package com.orderflow.inventory

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

/**
 * Configuración del punto de entrada de Spring Boot para Inventory Service.
 *
 * Situar [SpringBootApplication] en el paquete raíz `com.orderflow.inventory` hace que Spring examine
 * todos los adaptadores, servicios de aplicación y clases de configuración que contiene. La clase
 * está vacía de forma intencionada porque su responsabilidad es declarativa: marca la frontera de
 * este servicio desplegable de forma independiente y activa la configuración automática de Spring Boot.
 */
@SpringBootApplication
class InventoryServiceApplication

/**
 * Inicia Inventory Service como un proceso JVM autónomo.
 *
 * Los argumentos de línea de comandos se reenvían sin cambios a Spring Boot para que sigan
 * funcionando opciones estándar como la selección de perfiles y la sobrescritura de configuración.
 *
 * @param args argumentos recibidos del sistema operativo al iniciar el proceso.
 */
fun main(args: Array<String>) {
    runApplication<InventoryServiceApplication>(*args)
}
