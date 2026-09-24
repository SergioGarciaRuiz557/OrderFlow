package com.orderflow.order.domain.model;

import com.orderflow.order.domain.exception.DomainInvariantViolationException;

/**
 * Número positivo de unidades de producto solicitadas por una línea de pedido.
 *
 * @param value número de unidades; debe ser mayor que cero
 */
public record Quantity(int value) {
    /** Aplica la invariante que exige una cantidad estrictamente positiva. */
    public Quantity {
        if (value <= 0) {
            throw new DomainInvariantViolationException("Quantity must be greater than zero");
        }
    }
}
