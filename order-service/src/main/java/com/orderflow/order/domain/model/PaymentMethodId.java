package com.orderflow.order.domain.model;

import com.orderflow.order.domain.exception.DomainInvariantViolationException;

public record PaymentMethodId(String value) {
    public PaymentMethodId {
        if (value == null || value.isBlank()) {
            throw new DomainInvariantViolationException("Payment method id is required");
        }
        value = value.trim();
    }
}
