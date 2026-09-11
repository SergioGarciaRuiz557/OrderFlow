package com.orderflow.inventory.application.service

import com.orderflow.inventory.application.port.`in`.*
import com.orderflow.inventory.application.port.`out`.InventoryEventPublisher
import com.orderflow.inventory.application.port.`out`.InventoryRepository
import com.orderflow.inventory.domain.model.*
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test

class InventoryMessagingServiceTest {
    @Test fun `later item rejection releases reservations made by the same order command`() {
        val reserve = mockk<ReserveInventoryUseCase>()
        val release = mockk<ReleaseInventoryUseCase>(relaxed = true)
        val repository = mockk<InventoryRepository>(relaxed = true)
        val events = mockk<InventoryEventPublisher>(relaxed = true)
        val accepted = mockk<ReservationResult.Reserved>()
        val reservationId = ReservationId.new()
        every { accepted.reservation.id } returns reservationId
        val rejected = ReservationResult.Rejected(ReservationRejectionReason.INSUFFICIENT_STOCK, 0)
        every { reserve.reserve(any()) } returnsMany listOf(accepted, rejected)
        val orderId = OrderId("550e8400-e29b-41d4-a716-446655440000")
        val command = ReserveOrderInventoryCommand(orderId, listOf(
            ReservationItem(ProductId("PRODUCT-001"), Quantity(1)),
            ReservationItem(ProductId("PRODUCT-002"), Quantity(1)),
        ))

        InventoryMessagingService(reserve, release, repository, events).reserve(command)

        verify(exactly = 1) { release.release(reservationId) }
        verify(exactly = 1) { events.inventoryRejected(orderId, rejected) }
        verify(exactly = 0) { events.inventoryReserved(any(), any()) }
    }
}
