package com.orderflow.order.adapter.in.rest;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * JSON request accepted by {@code POST /api/orders}.
 *
 * <p>Bean Validation protects the HTTP boundary and produces useful client errors. Equivalent core
 * invariants are still enforced by domain value objects because other adapters may call the use case.</p>
 *
 * @param customerId required customer UUID
 * @param items non-empty, recursively validated item collection
 * @param paymentMethodId required non-blank payment reference
 */
public record CreateOrderRequest(
        @NotNull UUID customerId,
        @NotEmpty List<@Valid Item> items,
        @NotBlank String paymentMethodId) {

    /**
     * One item supplied in the creation request.
     *
     * @param productId required catalogue identifier
     * @param quantity strictly-positive unit count
     * @param unitPrice required non-negative decimal price; floating-point values are never used
     */
    public record Item(
            @NotBlank String productId,
            @Positive int quantity,
            @NotNull @DecimalMin(value = "0.00") BigDecimal unitPrice) {
    }
}
