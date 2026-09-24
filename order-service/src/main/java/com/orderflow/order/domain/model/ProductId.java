package com.orderflow.order.domain.model;

import com.orderflow.order.domain.exception.DomainInvariantViolationException;

/**
 * Referencia al catálogo de productos con tipo fuerte que utiliza una línea de pedido.
 *
 * @param value identificador de producto no vacío; se eliminan los espacios en blanco de los extremos
 */
public record ProductId(String value) {
    /** Normaliza y valida la referencia del catálogo. */
    public ProductId {
        if (value == null || value.isBlank()) {
            throw new DomainInvariantViolationException("Product id is required");
        }
        value = value.trim();
    }
}
