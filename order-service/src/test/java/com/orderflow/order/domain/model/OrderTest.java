package com.orderflow.order.domain.model;

import com.orderflow.order.domain.event.InventoryReleaseRequested;
import com.orderflow.order.domain.event.OrderConfirmed;
import com.orderflow.order.domain.exception.DomainInvariantViolationException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderTest {
    private static final Instant NOW = Instant.parse("2026-09-07T10:00:00Z");

    @Test
    void shouldCreateValidOrder() {
        Order order = newOrder(List.of(line("PRODUCT-001", 2, "59.99")));

        assertThat(order.status()).isEqualTo(OrderStatus.PENDING);
        assertThat(order.total()).isEqualTo(Money.eur(new BigDecimal("119.98")));
    }

    @Test
    void shouldRejectEmptyOrder() {
        assertThatThrownBy(() -> newOrder(List.of()))
                .isInstanceOf(DomainInvariantViolationException.class)
                .hasMessageContaining("at least one line");
    }

    @Test
    void shouldRejectInvalidQuantityAndNegativePrice() {
        assertThatThrownBy(() -> new Quantity(0))
                .isInstanceOf(DomainInvariantViolationException.class);
        assertThatThrownBy(() -> Money.eur(new BigDecimal("-0.01")))
                .isInstanceOf(DomainInvariantViolationException.class);
    }

    @Test
    void shouldCalculateOrderTotal() {
        Order order = newOrder(List.of(line("A", 2, "10.25"), line("B", 3, "4.50")));

        assertThat(order.total().amount()).isEqualByComparingTo("34.00");
    }

    @Test
    void shouldNotAuthorizePaymentBeforeInventoryReservation() {
        Order order = newOrder(List.of(line("A", 1, "10.00")));

        assertThatThrownBy(() -> order.requestPaymentAuthorization(NOW))
                .isInstanceOf(DomainInvariantViolationException.class)
                .hasMessageContaining("before inventory reservation");
    }

    @Test
    void shouldConfirmOrderAfterPaymentAuthorization() {
        Order order = orderAwaitingPayment();
        order.pullDomainEvents();

        order.markPaymentAuthorized(NOW.plusSeconds(3));

        assertThat(order.status()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(order.pullDomainEvents()).anyMatch(OrderConfirmed.class::isInstance);
    }

    @Test
    void shouldNotCancelConfirmedOrder() {
        Order order = orderAwaitingPayment();
        order.markPaymentAuthorized(NOW.plusSeconds(3));

        assertThatThrownBy(() -> order.requestCancellation("customer request", NOW.plusSeconds(4)))
                .isInstanceOf(DomainInvariantViolationException.class)
                .hasMessageContaining("confirmed order");
    }

    @Test
    void shouldCancelOrderWhenInventoryIsRejected() {
        Order order = newOrder(List.of(line("A", 1, "10.00")));
        order.requestInventoryReservation(NOW.plusSeconds(1));

        order.markInventoryRejected("out of stock", NOW.plusSeconds(2));

        assertThat(order.status()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    void shouldRequestInventoryReleaseWhenPaymentIsRejected() {
        Order order = orderAwaitingPayment();
        order.pullDomainEvents();

        order.markPaymentRejected("declined", NOW.plusSeconds(3));

        assertThat(order.status()).isEqualTo(OrderStatus.CANCELLATION_PENDING);
        assertThat(order.pullDomainEvents()).anyMatch(InventoryReleaseRequested.class::isInstance);
    }

    @Test
    void shouldSafelyIgnoreDuplicateSuccessfulTransition() {
        Order order = orderAwaitingPayment();
        order.markPaymentAuthorized(NOW.plusSeconds(3));
        order.pullDomainEvents();

        order.markPaymentAuthorized(NOW.plusSeconds(4));

        assertThat(order.status()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(order.pullDomainEvents()).isEmpty();
    }

    private Order orderAwaitingPayment() {
        Order order = newOrder(List.of(line("A", 1, "10.00")));
        order.requestInventoryReservation(NOW.plusSeconds(1));
        order.markInventoryReserved(NOW.plusSeconds(2));
        order.requestPaymentAuthorization(NOW.plusSeconds(2));
        return order;
    }

    private Order newOrder(List<OrderLine> lines) {
        return Order.create(new OrderId(UUID.randomUUID()), new CustomerId(UUID.randomUUID()), lines,
                new PaymentMethodId("pm-test"), NOW);
    }

    private OrderLine line(String product, int quantity, String price) {
        return new OrderLine(new ProductId(product), new Quantity(quantity), Money.eur(new BigDecimal(price)));
    }
}
