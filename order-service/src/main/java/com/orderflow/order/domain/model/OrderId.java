package com.orderflow.order.domain.model;

import com.orderflow.order.domain.exception.DomainInvariantViolationException;

import java.util.Objects;
import java.util.UUID;

/**
 * Strongly typed identity of an {@link Order} aggregate.
 *
 * @param value non-null UUID stored in the database and exposed by the API
 */
public record OrderId(UUID value) {
    /** Validates that an Order identity is present. */
    public OrderId {
        Objects.requireNonNull(value, "Order id is required");
    }

    /**
     * Generates a new random aggregate identity for the production ID provider.
     *
     * @return newly generated identity
     */
    public static OrderId newId() {
        return new OrderId(UUID.randomUUID());
    }

    /**
     * Parses the textual UUID representation used by external boundaries.
     *
     * @param value textual UUID
     * @return typed Order identity
     * @throws DomainInvariantViolationException when the text is not a valid UUID
     */
    public static OrderId from(String value) {
        try {
            return new OrderId(UUID.fromString(value));
        } catch (IllegalArgumentException exception) {
            throw new DomainInvariantViolationException("Invalid order id: " + value);
        }
    }

    /**
     * Returns the canonical UUID text without the record wrapper.
     *
     * @return canonical UUID text
     */
    @Override
    public String toString() {
        return value.toString();
    }
}
