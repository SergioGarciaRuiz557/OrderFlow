package com.orderflow.order.application.port.in;

import java.util.UUID;

/** Inbound callback boundary for a successful inventory reservation result. */
public interface HandleInventoryReservedUseCase {
    /**
     * Records inventory success and advances the order to payment authorization.
     *
     * @param orderId order referenced by the external result
     */
    void handle(UUID orderId);
}
