package com.orderflow.order.application.service;

import com.orderflow.order.application.port.out.ClockProvider;
import com.orderflow.order.application.port.out.IntegrationMessagePublisher;
import com.orderflow.order.application.port.out.OrderRepository;
import com.orderflow.order.domain.model.CustomerId;
import com.orderflow.order.domain.model.Money;
import com.orderflow.order.domain.model.Order;
import com.orderflow.order.domain.model.OrderId;
import com.orderflow.order.domain.model.OrderLine;
import com.orderflow.order.domain.model.OrderStatus;
import com.orderflow.order.domain.model.PaymentMethodId;
import com.orderflow.order.domain.model.ProductId;
import com.orderflow.order.domain.model.Quantity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HandleInventoryReservedServiceTest {
    @Mock OrderRepository repository;
    @Mock IntegrationMessagePublisher publisher;
    @Mock ClockProvider clock;

    @Test
    void shouldMoveOrderToPaymentPending() {
        UUID id = UUID.randomUUID();
        Instant now = Instant.parse("2026-09-07T10:00:00Z");
        Order order = Order.create(new OrderId(id), new CustomerId(UUID.randomUUID()),
                List.of(new OrderLine(new ProductId("A"), new Quantity(1), Money.eur(BigDecimal.TEN))),
                new PaymentMethodId("pm-test"), now);
        order.requestInventoryReservation(now);
        order.pullDomainEvents();
        when(repository.findById(new OrderId(id))).thenReturn(Optional.of(order));
        when(clock.now()).thenReturn(now.plusSeconds(1));
        HandleInventoryReservedService service = new HandleInventoryReservedService(repository, publisher, clock);

        service.handle(id);

        assertThat(order.status()).isEqualTo(OrderStatus.PAYMENT_PENDING);
        verify(repository).save(order);
    }
}
