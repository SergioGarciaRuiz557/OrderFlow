package com.orderflow.order.application.exception;

import com.orderflow.order.domain.model.OrderId;

/**
 * Signals that an application use case could not load the requested aggregate.
 *
 * <p>Keeping this separate from repository or JPA exceptions allows every inbound adapter to map
 * absence according to its own protocol.</p>
 */
public class OrderNotFoundException extends RuntimeException {
    /**
     * Creates an exception whose message includes the missing identity.
     *
     * @param orderId identifier requested by the caller
     */
    public OrderNotFoundException(OrderId orderId) {
        super("Order not found: " + orderId);
    }
}
