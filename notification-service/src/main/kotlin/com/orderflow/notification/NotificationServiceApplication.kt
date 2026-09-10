package com.orderflow.notification

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

/**
 * Spring Boot entry-point configuration for Notification Service.
 *
 * This class has no business behavior. Its only responsibility is to define the root of the Spring
 * application context. Because it lives in the `com.orderflow.notification` root package,
 * component scanning discovers the application services and the fake outbound adapter located in
 * its subpackages.
 *
 * [SpringBootApplication] combines Spring's configuration, component-scanning, and
 * auto-configuration facilities. Keeping that framework concern here prevents the domain model and
 * application ports from depending on Spring Boot.
 */
@SpringBootApplication
class NotificationServiceApplication

/**
 * Starts Notification Service as an independently executable process.
 *
 * [runApplication] creates the Spring application context, applies the configuration from
 * `application.yml`, discovers the service beans, and keeps the process running. The spread
 * operator (`*`) forwards every command-line argument received by this process to Spring Boot.
 *
 * @param args optional command-line arguments supplied to the application process.
 */
fun main(args: Array<String>) {
    runApplication<NotificationServiceApplication>(*args)
}
