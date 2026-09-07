package com.orderflow.order.application.port.in;

import java.util.UUID;

/** Inbound callback boundary for a rejected inventory reservation result. */
public interface HandleInventoryRejectedUseCase {
    /**
     * Records inventory rejection and cancels the order.
     *
     * @param orderId order referenced by the external result
     * @param reason rejection explanation supplied by Inventory
     */
    void handle(UUID orderId, String reason);
}
