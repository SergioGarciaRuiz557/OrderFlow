package com.orderflow.order.application.port.out;

import com.orderflow.order.domain.event.OrderDomainEvent;

import java.util.List;

/**
 * Outbound boundary for delivering business facts beyond the local transaction.
 *
 * <p>The Kafka adapter translates supported domain events into versioned integration messages while
 * this application interface remains transport-neutral.</p>
 */
public interface IntegrationMessagePublisher {
    /**
     * Publishes events produced by one aggregate operation in their original order.
     *
     * @param events immutable event batch; may be empty after an idempotent callback
     */
    void publish(List<OrderDomainEvent> events);
}
