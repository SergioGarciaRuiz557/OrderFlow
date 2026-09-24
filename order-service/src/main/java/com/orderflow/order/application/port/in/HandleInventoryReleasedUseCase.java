package com.orderflow.order.application.port.in;

import java.util.UUID;

/** Límite de entrada para completar la cancelación después de que Inventory confirme la compensación. */
public interface HandleInventoryReleasedUseCase {
    void handle(UUID orderId);
}
