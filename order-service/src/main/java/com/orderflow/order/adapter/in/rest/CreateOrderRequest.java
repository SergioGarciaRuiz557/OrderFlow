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
 * Petición JSON aceptada por {@code POST /api/orders}.
 *
 * <p>Bean Validation protege el límite HTTP y produce errores útiles para el cliente. Los objetos de valor del dominio
 * siguen aplicando las invariantes esenciales equivalentes porque otros adaptadores pueden llamar al caso de uso.</p>
 *
 * @param customerId UUID obligatorio del cliente
 * @param items colección de artículos no vacía y validada de forma recursiva
 * @param paymentMethodId referencia de pago obligatoria y no vacía
 */
public record CreateOrderRequest(
        @NotNull UUID customerId,
        @NotEmpty List<@Valid Item> items,
        @NotBlank String paymentMethodId) {

    /**
     * Un artículo proporcionado en la petición de creación.
     *
     * @param productId identificador obligatorio del catálogo
     * @param quantity número de unidades estrictamente positivo
     * @param unitPrice precio decimal obligatorio y no negativo; nunca se utilizan valores de coma flotante
     */
    public record Item(
            @NotBlank String productId,
            @Positive int quantity,
            @NotNull @DecimalMin(value = "0.00") BigDecimal unitPrice) {
    }
}
