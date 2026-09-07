package com.orderflow.order.adapter.out.messaging;

import com.orderflow.order.application.port.out.IntegrationMessagePublisher;
import com.orderflow.order.domain.event.OrderDomainEvent;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Development-safe publisher used before transport infrastructure exists.
 *
 * <p>It satisfies the application port so local REST and persistence work without Kafka. It does not
 * claim delivery or serialize domain events; a later adapter must replace it when cross-service
 * communication is introduced.</p>
 */
@Component
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
        // A Kafka adapter will replace this local sink in a later integration commit.
    }
}
