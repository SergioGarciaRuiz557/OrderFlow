package com.orderflow.order.domain.model;

import java.util.Objects;

/**
 * Línea inmutable perteneciente a un agregado {@link Order}.
 *
 * @param productId producto que se compra
 * @param quantity número de unidades estrictamente positivo
 * @param unitPrice precio no negativo de una unidad
 */
public record OrderLine(ProductId productId, Quantity quantity, Money unitPrice) {
    /** Garantiza que existan todos los componentes de la línea aunque esta se cree fuera del adaptador REST. */
    public OrderLine {
        Objects.requireNonNull(productId, "Product id is required");
        Objects.requireNonNull(quantity, "Quantity is required");
        Objects.requireNonNull(unitPrice, "Unit price is required");
    }

    /**
     * Calcula la aportación monetaria de esta línea al total del pedido.
     *
     * @return precio unitario multiplicado por la cantidad
     */
    public Money subtotal() {
        return unitPrice.multiply(quantity);
    }
}
