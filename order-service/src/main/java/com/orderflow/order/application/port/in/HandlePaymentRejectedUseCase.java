package com.orderflow.order.application.port.in;

import java.util.UUID;

public interface HandlePaymentRejectedUseCase {
    void handle(UUID orderId, String reason);
}
