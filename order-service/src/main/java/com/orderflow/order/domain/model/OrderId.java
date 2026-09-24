package com.orderflow.order.domain.model;

import com.orderflow.order.domain.exception.DomainInvariantViolationException;

import java.util.Objects;
import java.util.UUID;

/**
 * Identidad con tipo fuerte de un agregado {@link Order}.
 *
 * @param value UUID no nulo almacenado en la base de datos y expuesto por la API
 */
public record OrderId(UUID value) {
    /** Valida que esté presente una identidad de Order. */
    public OrderId {
        Objects.requireNonNull(value, "Order id is required");
    }

    /**
     * Genera una identidad aleatoria nueva del agregado para el proveedor de identificadores de producción.
     *
     * @return identidad recién generada
     */
    public static OrderId newId() {
        return new OrderId(UUID.randomUUID());
    }

    /**
     * Analiza la representación textual del UUID utilizada por los límites externos.
     *
     * @param value UUID textual
     * @return identidad de Order con tipo
     * @throws DomainInvariantViolationException cuando el texto no es un UUID válido
     */
    public static OrderId from(String value) {
        try {
            return new OrderId(UUID.fromString(value));
        } catch (IllegalArgumentException exception) {
            throw new DomainInvariantViolationException("Invalid order id: " + value);
        }
    }

    /**
     * Devuelve el texto canónico del UUID sin el envoltorio del record.
     *
     * @return texto canónico del UUID
     */
    @Override
    public String toString() {
        return value.toString();
    }
}
