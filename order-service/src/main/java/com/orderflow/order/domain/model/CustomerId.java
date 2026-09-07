package com.orderflow.order.domain.model;

import java.util.Objects;
import java.util.UUID;

/**
 * Strongly typed reference to the customer who owns an order.
 *
 * @param value non-null external customer UUID
 */
public record CustomerId(UUID value) {
    /** Validates that the customer reference is present. */
    public CustomerId {
        Objects.requireNonNull(value, "Customer id is required");
    }
}
