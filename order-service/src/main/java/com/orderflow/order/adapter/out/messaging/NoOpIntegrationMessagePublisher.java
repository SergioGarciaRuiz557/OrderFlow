package com.orderflow.order.adapter.out.messaging;

import com.orderflow.order.application.port.out.IntegrationMessagePublisher;
import com.orderflow.order.domain.event.OrderDomainEvent;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.util.List;

/**
 * Publicador seguro para desarrollo que solo se utiliza cuando Kafka está deshabilitado explícitamente.
 *
 * <p>Satisface el puerto de la aplicación para que REST y la persistencia locales funcionen sin Kafka. No
 * confirma la entrega ni serializa eventos de dominio. En producción se utiliza de forma predeterminada la implementación de Kafka.</p>
 */
@Component
@ConditionalOnProperty(name = "orderflow.kafka.enabled", havingValue = "false")
public class NoOpIntegrationMessagePublisher implements IntegrationMessagePublisher {
    /** Crea el publicador local sin estado que descubre el escaneo de componentes de Spring. */
    public NoOpIntegrationMessagePublisher() {
    }

    /**
     * Acepta y descarta deliberadamente el lote actual de eventos de dominio.
     *
     * @param events eventos ya persistidos como estado del agregado; actualmente no se transportan
     */
    @Override
    public void publish(List<OrderDomainEvent> events) {
        // Deshabilitar expresamente la mensajería mantiene deterministas las pruebas aisladas de persistencia y HTTP.
    }
}
