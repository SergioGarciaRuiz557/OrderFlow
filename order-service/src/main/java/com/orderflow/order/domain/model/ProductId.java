package com.orderflow.order.domain.model;

import com.orderflow.order.domain.exception.DomainInvariantViolationException;

public record ProductId(String value) {
    public ProductId {
        if (value == null || value.isBlank()) {
            throw new DomainInvariantViolationException("Product id is required");
        }
        value = value.trim();
    }
}
