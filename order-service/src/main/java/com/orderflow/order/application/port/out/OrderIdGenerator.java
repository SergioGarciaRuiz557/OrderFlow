package com.orderflow.order.application.port.out;

import com.orderflow.order.domain.model.OrderId;

/** Supplies new aggregate identities and permits deterministic generators in unit tests. */
public interface OrderIdGenerator {
    /**
     * Generates the identity for a new aggregate.
     *
     * @return identity that has not previously been assigned to an order
     */
    OrderId nextId();
}
