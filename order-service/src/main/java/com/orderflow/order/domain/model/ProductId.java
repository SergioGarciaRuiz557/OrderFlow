package com.orderflow.order.domain.model;

import com.orderflow.order.domain.exception.DomainInvariantViolationException;

/**
 * Strongly typed product catalogue reference used by an order line.
 *
 * @param value non-blank product identifier; surrounding whitespace is removed
 */
public record ProductId(String value) {
    /** Normalizes and validates the catalogue reference. */
    public ProductId {
        if (value == null || value.isBlank()) {
            throw new DomainInvariantViolationException("Product id is required");
        }
        value = value.trim();
    }
}
