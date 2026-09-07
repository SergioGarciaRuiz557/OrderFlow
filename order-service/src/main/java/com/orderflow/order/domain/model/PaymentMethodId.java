package com.orderflow.order.domain.model;

import com.orderflow.order.domain.exception.DomainInvariantViolationException;

/**
 * Opaque reference to the payment method that a future Payment adapter will authorize.
 *
 * @param value non-blank provider-independent reference; surrounding whitespace is removed
 */
public record PaymentMethodId(String value) {
    /** Normalizes and validates the payment-method reference. */
    public PaymentMethodId {
        if (value == null || value.isBlank()) {
            throw new DomainInvariantViolationException("Payment method id is required");
        }
        value = value.trim();
    }
}
