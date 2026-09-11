package com.orderflow.inventory.adapter.kafka

import com.fasterxml.jackson.databind.ObjectMapper
import com.orderflow.inventory.adapter.`in`.kafka.InventoryCommandsKafkaListener
import com.orderflow.inventory.application.port.`in`.InventoryMessagingUseCase
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class InventoryContractCompatibilityTest {
    @Test fun `consumes the Java Order fixture without a shared DTO library`() {
        val useCase = mockk<InventoryMessagingUseCase>(relaxed = true)
        InventoryCommandsKafkaListener(ObjectMapper(), useCase).listen(fixture("ReserveInventoryCommand.json"))
        verify(exactly = 1) { useCase.reserve(any()) }
    }
    private fun fixture(name: String) = Files.readString(Path.of("..", "docs", "messaging", "contracts", name))
}
