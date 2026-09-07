package com.orderflow.order;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Starts the Order Service Spring Boot application.
 *
 * <p>{@link SpringBootApplication} makes this class the root for component scanning and enables
 * Spring Boot auto-configuration for REST, validation, JPA, Flyway, and PostgreSQL.</p>
 */
@SpringBootApplication
public class OrderServiceApplication {

    /** Creates the bootstrap component; Spring Boot invokes the static entry point in production. */
    public OrderServiceApplication() {
    }

    /**
     * Application process entry point.
     *
     * @param args command-line arguments forwarded to Spring Boot
     */
    public static void main(String[] args) {
        SpringApplication.run(OrderServiceApplication.class, args);
    }
}
