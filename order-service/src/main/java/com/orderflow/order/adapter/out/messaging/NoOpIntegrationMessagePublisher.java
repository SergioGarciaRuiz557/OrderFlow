package com.orderflow.order.adapter.out.messaging;

import com.orderflow.order.application.port.out.IntegrationMessagePublisher;
import com.orderflow.order.domain.event.OrderDomainEvent;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.util.List;

/**
 * Development-safe publisher used only when Kafka is explicitly disabled.
 *
 * <p>It satisfies the application port so local REST and persistence work without Kafka. It does not
 * claim delivery or serialize domain events. Production defaults to the Kafka implementation.</p>
 */
@Component
@ConditionalOnProperty(name = "orderflow.kafka.enabled", havingValue = "false")
public class NoOpIntegrationMessagePublisher implements IntegrationMessagePublisher {
    /** Creates the stateless local publisher discovered by Spring component scanning. */
    public NoOpIntegrationMessagePublisher() {
    }

    /**
     * Accepts and intentionally discards the current domain-event batch.
     *
     * @param events events already persisted as aggregate state; currently not transported
     */
    @Override
    public void publish(List<OrderDomainEvent> events) {
        // Explicitly disabled messaging keeps isolated persistence/HTTP tests deterministic.
    }
}
