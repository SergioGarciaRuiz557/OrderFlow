package com.orderflow.order;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Inicia la aplicación Spring Boot de Order Service.
 *
 * <p>{@link SpringBootApplication} convierte esta clase en la raíz del escaneo de componentes y habilita
 * la configuración automática de Spring Boot para REST, validación, JPA, Flyway y PostgreSQL.</p>
 */
@SpringBootApplication
public class OrderServiceApplication {

    /** Crea el componente de arranque; Spring Boot invoca el punto de entrada estático en producción. */
    public OrderServiceApplication() {
    }

    /**
     * Punto de entrada del proceso de la aplicación.
     *
     * @param args argumentos de la línea de comandos que se reenvían a Spring Boot
     */
    public static void main(String[] args) {
        SpringApplication.run(OrderServiceApplication.class, args);
    }
}
