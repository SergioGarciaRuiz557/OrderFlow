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

public record CreateOrderRequest(
        @NotNull UUID customerId,
        @NotEmpty List<@Valid Item> items,
        @NotBlank String paymentMethodId) {

    public record Item(
            @NotBlank String productId,
            @Positive int quantity,
            @NotNull @DecimalMin(value = "0.00") BigDecimal unitPrice) {
    }
}
