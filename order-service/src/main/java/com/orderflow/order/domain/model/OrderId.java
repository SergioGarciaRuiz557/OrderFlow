package com.orderflow.order.domain.model;

import com.orderflow.order.domain.exception.DomainInvariantViolationException;

import java.util.Objects;
import java.util.UUID;

public record OrderId(UUID value) {
    public OrderId {
        Objects.requireNonNull(value, "Order id is required");
    }

    public static OrderId newId() {
        return new OrderId(UUID.randomUUID());
    }

    public static OrderId from(String value) {
        try {
            return new OrderId(UUID.fromString(value));
        } catch (IllegalArgumentException exception) {
            throw new DomainInvariantViolationException("Invalid order id: " + value);
        }
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
