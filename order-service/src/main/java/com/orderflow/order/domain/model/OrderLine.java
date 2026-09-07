package com.orderflow.order.domain.model;

import java.util.Objects;

/**
 * Immutable line belonging to an {@link Order} aggregate.
 *
 * @param productId product being purchased
 * @param quantity strictly-positive number of units
 * @param unitPrice non-negative price of one unit
 */
public record OrderLine(ProductId productId, Quantity quantity, Money unitPrice) {
    /** Ensures all line components exist even when the line is created outside the REST adapter. */
    public OrderLine {
        Objects.requireNonNull(productId, "Product id is required");
        Objects.requireNonNull(quantity, "Quantity is required");
        Objects.requireNonNull(unitPrice, "Unit price is required");
    }

    /**
     * Calculates this line's monetary contribution to the order total.
     *
     * @return unit price multiplied by quantity
     */
    public Money subtotal() {
        return unitPrice.multiply(quantity);
    }
}
