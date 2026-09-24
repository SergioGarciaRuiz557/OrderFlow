package com.orderflow.order.domain.model;

import com.orderflow.order.domain.exception.DomainInvariantViolationException;

/**
 * Referencia opaca al método de pago que autorizará un adaptador de Payment.
 *
 * @param value referencia no vacía e independiente del proveedor; se eliminan los espacios en blanco de los extremos
 */
public record PaymentMethodId(String value) {
    /** Normaliza y valida la referencia del método de pago. */
    public PaymentMethodId {
        if (value == null || value.isBlank()) {
            throw new DomainInvariantViolationException("Payment method id is required");
        }
        value = value.trim();
    }
}
