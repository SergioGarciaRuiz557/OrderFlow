package com.orderflow.order.domain.model;

import java.util.Objects;

public record OrderLine(ProductId productId, Quantity quantity, Money unitPrice) {
    public OrderLine {
        Objects.requireNonNull(productId, "Product id is required");
        Objects.requireNonNull(quantity, "Quantity is required");
        Objects.requireNonNull(unitPrice, "Unit price is required");
    }

    public Money subtotal() {
        return unitPrice.multiply(quantity);
    }
}
