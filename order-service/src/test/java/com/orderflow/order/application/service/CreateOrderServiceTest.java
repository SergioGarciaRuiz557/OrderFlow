package com.orderflow.order.application.service;

import com.orderflow.order.application.port.in.CreateOrderUseCase.CreateOrderCommand;
import com.orderflow.order.application.port.in.CreateOrderUseCase.CreateOrderItem;
import com.orderflow.order.application.port.out.ClockProvider;
import com.orderflow.order.application.port.out.IntegrationMessagePublisher;
import com.orderflow.order.application.port.out.OrderIdGenerator;
import com.orderflow.order.application.port.out.OrderRepository;
import com.orderflow.order.domain.event.InventoryReservationRequested;
import com.orderflow.order.domain.event.OrderDomainEvent;
import com.orderflow.order.domain.model.Order;
import com.orderflow.order.domain.model.OrderId;
import com.orderflow.order.domain.model.OrderStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Unit test for creation orchestration with every outbound dependency mocked. */
@ExtendWith(MockitoExtension.class)
class CreateOrderServiceTest {
    /** Captures persistence interaction without a database. */
    @Mock OrderRepository repository;
    /** Captures emitted domain facts without messaging infrastructure. */
    @Mock IntegrationMessagePublisher publisher;
    /** Provides deterministic application time. */
    @Mock ClockProvider clock;
    /** Provides a deterministic identity. */
    @Mock OrderIdGenerator idGenerator;

    /** Verifies mapping, aggregate behavior, persistence, result, and event publication together. */
    @Test
    void shouldCreatePersistAndPublishInventoryReservation() {
        UUID orderUuid = UUID.randomUUID();
        Instant now = Instant.parse("2026-09-07T10:00:00Z");
        when(clock.now()).thenReturn(now);
        when(idGenerator.nextId()).thenReturn(new OrderId(orderUuid));
        when(repository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));
        CreateOrderService service = new CreateOrderService(repository, publisher, clock, idGenerator);
        CreateOrderCommand command = new CreateOrderCommand(UUID.randomUUID(),
                List.of(new CreateOrderItem("PRODUCT-001", 2, new BigDecimal("59.99"))), "pm-test");

        var result = service.create(command);

        assertThat(result.orderId()).isEqualTo(orderUuid);
        assertThat(result.status()).isEqualTo(OrderStatus.INVENTORY_RESERVATION_PENDING);
        assertThat(result.total()).isEqualByComparingTo("119.98");
        verify(repository).save(any(Order.class));
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<OrderDomainEvent>> events = ArgumentCaptor.forClass(List.class);
        verify(publisher).publish(events.capture());
        assertThat(events.getValue()).anyMatch(InventoryReservationRequested.class::isInstance);
    }
}
