package com.orderflow.inventory

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

/**
 * Spring Boot entry-point configuration for the Inventory Service.
 *
 * Placing [SpringBootApplication] at the root `com.orderflow.inventory` package makes Spring scan
 * all adapters, application services, and configuration classes below this package. The class is
 * intentionally empty because its responsibility is declarative: it marks the boundary of this
 * independently deployable service and enables Spring Boot auto-configuration.
 */
@SpringBootApplication
class InventoryServiceApplication

/**
 * Starts the Inventory Service as a standalone JVM process.
 *
 * Command-line arguments are forwarded unchanged to Spring Boot so standard options such as
 * profile selection and configuration overrides continue to work.
 *
 * @param args arguments received from the operating system when the process is launched.
 */
fun main(args: Array<String>) {
    runApplication<InventoryServiceApplication>(*args)
}
