package com.orderflow.order.application.port.in;

import java.util.UUID;

public interface HandlePaymentAuthorizedUseCase {
    void handle(UUID orderId);
}
