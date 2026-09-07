package com.orderflow.order.application.port.in;

import java.util.UUID;

/** Inbound callback boundary for rejected payment authorization. */
public interface HandlePaymentRejectedUseCase {
    /**
     * Records payment rejection and begins inventory-release compensation.
     *
     * @param orderId order referenced by the external result
     * @param reason rejection explanation supplied by Payment
     */
    void handle(UUID orderId, String reason);
}
