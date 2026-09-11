package com.orderflow.notification.adapter.`in`.kafka

import com.fasterxml.jackson.databind.ObjectMapper
import com.orderflow.notification.application.port.`in`.SendOrderCancelledNotificationUseCase
import com.orderflow.notification.application.port.`in`.SendOrderConfirmedNotificationUseCase
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class NotificationContractCompatibilityTest {
    @Test fun `consumes Java Order fixtures without a shared DTO library`() {
        val confirmed = mockk<SendOrderConfirmedNotificationUseCase>(relaxed = true)
        val cancelled = mockk<SendOrderCancelledNotificationUseCase>(relaxed = true)
        val listener = OrderEventsKafkaListener(ObjectMapper(), confirmed, cancelled)
        listener.listen(fixture("OrderConfirmedEvent.json"))
        listener.listen(fixture("OrderCancelledEvent.json"))
        verify(exactly = 1) { confirmed.send(any()) }
        verify(exactly = 1) { cancelled.send(any()) }
    }
    private fun fixture(name: String) = Files.readString(Path.of("..", "docs", "messaging", "contracts", name))
}
