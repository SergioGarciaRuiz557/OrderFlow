package com.orderflow.order.application.port.in;

import java.util.UUID;

/** Inbound boundary for completing cancellation after Inventory confirms compensation. */
public interface HandleInventoryReleasedUseCase {
    void handle(UUID orderId);
}
