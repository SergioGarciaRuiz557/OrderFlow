package com.orderflow.payment.adapter.kafka

import com.fasterxml.jackson.databind.ObjectMapper
import com.orderflow.payment.adapter.`in`.kafka.AuthorizePaymentKafkaListener
import com.orderflow.payment.application.port.`in`.PaymentMessagingUseCase
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class PaymentContractCompatibilityTest {
    @Test fun `consumes the Java Order fixture without a shared DTO library`() {
        val useCase = mockk<PaymentMessagingUseCase>(relaxed = true)
        AuthorizePaymentKafkaListener(ObjectMapper(), useCase).listen(fixture("AuthorizePaymentCommand.json"))
        verify(exactly = 1) { useCase.authorize(any()) }
    }
    private fun fixture(name: String) = Files.readString(Path.of("..", "docs", "messaging", "contracts", name))
}
