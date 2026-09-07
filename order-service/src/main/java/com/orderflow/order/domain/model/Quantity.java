package com.orderflow.order.domain.model;

import com.orderflow.order.domain.exception.DomainInvariantViolationException;

/**
 * Positive number of product units requested by an order line.
 *
 * @param value number of units; must be greater than zero
 */
public record Quantity(int value) {
    /** Enforces the strictly-positive quantity invariant. */
    public Quantity {
        if (value <= 0) {
            throw new DomainInvariantViolationException("Quantity must be greater than zero");
        }
    }
}
