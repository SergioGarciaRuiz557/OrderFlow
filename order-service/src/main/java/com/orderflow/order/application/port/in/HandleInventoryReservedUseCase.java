package com.orderflow.order.application.port.in;

import java.util.UUID;

public interface HandleInventoryReservedUseCase {
    void handle(UUID orderId);
}
