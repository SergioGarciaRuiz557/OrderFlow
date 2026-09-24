package com.orderflow.order.domain.model;

import java.util.Objects;
import java.util.UUID;

/**
 * Referencia con tipo fuerte al cliente propietario de un pedido.
 *
 * @param value UUID externo no nulo del cliente
 */
public record CustomerId(UUID value) {
    /** Valida que esté presente la referencia del cliente. */
    public CustomerId {
        Objects.requireNonNull(value, "Customer id is required");
    }
}
