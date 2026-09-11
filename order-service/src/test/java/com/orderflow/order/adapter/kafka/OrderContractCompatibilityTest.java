package com.orderflow.order.adapter.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orderflow.order.adapter.in.kafka.OrderEventsKafkaListener;
import com.orderflow.order.application.port.in.*;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class OrderContractCompatibilityTest {
    @Test void consumesKotlinInventoryAndPaymentFixturesWithoutSharedClasses() throws Exception {
        var inventoryReserved = mock(HandleInventoryReservedUseCase.class);
        var inventoryRejected = mock(HandleInventoryRejectedUseCase.class);
        var inventoryReleased = mock(HandleInventoryReleasedUseCase.class);
        var paymentAuthorized = mock(HandlePaymentAuthorizedUseCase.class);
        var paymentRejected = mock(HandlePaymentRejectedUseCase.class);
        var listener = new OrderEventsKafkaListener(new ObjectMapper(), inventoryReserved, inventoryRejected,
                inventoryReleased, paymentAuthorized, paymentRejected);
        listener.onInventoryEvent(fixture("InventoryReservedEvent.json"));
        listener.onPaymentEvent(fixture("PaymentAuthorizedEvent.json"));
        UUID orderId = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
        verify(inventoryReserved).handle(orderId);
        verify(paymentAuthorized).handle(orderId);
    }
    private static String fixture(String name) throws Exception {
        return Files.readString(Path.of("..", "docs", "messaging", "contracts", name));
    }
}
