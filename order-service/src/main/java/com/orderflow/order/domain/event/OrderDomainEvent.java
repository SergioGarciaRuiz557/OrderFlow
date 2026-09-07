package com.orderflow.order.domain.event;

import com.orderflow.order.domain.model.OrderId;

import java.time.Instant;

/**
 * Common contract for facts emitted by the Order aggregate.
 *
 * <p>The contract intentionally contains only domain data shared by all events. Delivery metadata,
 * Kafka keys, headers, schema versions, and serialization belong to a future outbound adapter.</p>
 */
public interface OrderDomainEvent {
    /**
     * Identifies the aggregate that produced this fact.
     *
     * @return aggregate that produced the event
     */
    OrderId orderId();

    /**
     * Identifies when the fact occurred according to the application clock.
     *
     * @return business time at which the transition occurred
     */
    Instant occurredAt();
}
