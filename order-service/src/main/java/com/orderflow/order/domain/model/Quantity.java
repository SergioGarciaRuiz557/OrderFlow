package com.orderflow.order.domain.model;

import com.orderflow.order.domain.exception.DomainInvariantViolationException;

public record Quantity(int value) {
    public Quantity {
        if (value <= 0) {
            throw new DomainInvariantViolationException("Quantity must be greater than zero");
        }
    }
}
