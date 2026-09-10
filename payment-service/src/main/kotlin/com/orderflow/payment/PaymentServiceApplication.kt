package com.orderflow.payment

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

/**
 * Spring Boot composition root for the Payment microservice.
 *
 * [SpringBootApplication] enables auto-configuration and component scanning from the
 * `com.orderflow.payment` package. It discovers application services, outbound adapters, JPA
 * repositories, and [com.orderflow.payment.configuration.PaymentConfiguration], wiring them through
 * their input and output port interfaces. Business behavior does not live in this bootstrap class.
 */
@SpringBootApplication
class PaymentServiceApplication

/**
 * JVM entry point used by the executable Spring Boot artifact.
 *
 * @param args command-line arguments forwarded unchanged to Spring Boot, allowing standard property
 * overrides such as profiles and configuration locations.
 */
fun main(args: Array<String>) {
    // Reified generic startup identifies PaymentServiceApplication as the primary configuration class.
    runApplication<PaymentServiceApplication>(*args)
}
