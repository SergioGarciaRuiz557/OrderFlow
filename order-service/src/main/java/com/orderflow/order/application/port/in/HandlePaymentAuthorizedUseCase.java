package com.orderflow.order.application.port.in;

import java.util.UUID;

/** Inbound callback boundary for successful payment authorization. */
public interface HandlePaymentAuthorizedUseCase {
    /**
     * Records payment success and confirms the order.
     *
     * @param orderId order referenced by the external result
     */
    void handle(UUID orderId);
}
