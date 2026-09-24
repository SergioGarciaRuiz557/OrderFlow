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

/**
 * Especificación rápida y sin framework de las invariantes y del comportamiento del ciclo de vida del agregado Order.
 * Cada prueba interactúa con el mismo comportamiento público que utilizan los servicios de aplicación.
 */
class OrderTest {
    /** Un instante fijo mantiene deterministas las aserciones de estado y eventos. */
    private static final Instant NOW = Instant.parse("2026-09-07T10:00:00Z");

    /** Verifica el estado inicial y el cálculo del total que pertenece al dominio. */
    @Test
    void shouldCreateValidOrder() {
        Order order = newOrder(List.of(line("PRODUCT-001", 2, "59.99")));

        assertThat(order.status()).isEqualTo(OrderStatus.PENDING);
        assertThat(order.total()).isEqualTo(Money.eur(new BigDecimal("119.98")));
    }

    /** Verifica que la construcción del agregado no pueda eludir la invariante de líneas no vacías. */
    @Test
    void shouldRejectEmptyOrder() {
        assertThatThrownBy(() -> newOrder(List.of()))
                .isInstanceOf(DomainInvariantViolationException.class)
                .hasMessageContaining("at least one line");
    }

    /** Verifica que los objetos de valor rechacen una cantidad cero y dinero negativo con independencia de REST. */
    @Test
    void shouldRejectInvalidQuantityAndNegativePrice() {
        assertThatThrownBy(() -> new Quantity(0))
                .isInstanceOf(DomainInvariantViolationException.class);
        assertThatThrownBy(() -> Money.eur(new BigDecimal("-0.01")))
                .isInstanceOf(DomainInvariantViolationException.class);
    }

    /** Verifica el cálculo del total con varios precios y cantidades. */
    @Test
    void shouldCalculateOrderTotal() {
        Order order = newOrder(List.of(line("A", 2, "10.25"), line("B", 3, "4.50")));

        assertThat(order.total().amount()).isEqualByComparingTo("34.00");
    }

    /** Verifica que el orden de la saga prohíba el pago antes de confirmar la reserva de inventario. */
    @Test
    void shouldNotAuthorizePaymentBeforeInventoryReservation() {
        Order order = newOrder(List.of(line("A", 1, "10.00")));

        assertThatThrownBy(() -> order.requestPaymentAuthorization(NOW))
                .isInstanceOf(DomainInvariantViolationException.class)
                .hasMessageContaining("before inventory reservation");
    }

    /** Verifica que un pago correcto produzca el estado y el evento terminales de confirmación. */
    @Test
    void shouldConfirmOrderAfterPaymentAuthorization() {
        Order order = orderAwaitingPayment();
        order.pullDomainEvents();

        order.markPaymentAuthorized(NOW.plusSeconds(3));

        assertThat(order.status()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(order.pullDomainEvents()).anyMatch(OrderConfirmed.class::isInstance);
    }

    /** Verifica que la ruta de cancelación previa a la confirmación no pueda cancelar un pedido confirmado. */
    @Test
    void shouldNotCancelConfirmedOrder() {
        Order order = orderAwaitingPayment();
        order.markPaymentAuthorized(NOW.plusSeconds(3));

        assertThatThrownBy(() -> order.requestCancellation("customer request", NOW.plusSeconds(4)))
                .isInstanceOf(DomainInvariantViolationException.class)
                .hasMessageContaining("confirmed order");
    }

    /** Verifica que el rechazo del inventario finalice directamente el pedido como cancelado. */
    @Test
    void shouldCancelOrderWhenInventoryIsRejected() {
        Order order = newOrder(List.of(line("A", 1, "10.00")));
        order.requestInventoryReservation(NOW.plusSeconds(1));

        order.markInventoryRejected("out of stock", NOW.plusSeconds(2));

        assertThat(order.status()).isEqualTo(OrderStatus.CANCELLED);
    }

    /** Verifica que el rechazo del pago inicie la compensación solicitando la liberación del inventario. */
    @Test
    void shouldRequestInventoryReleaseWhenPaymentIsRejected() {
        Order order = orderAwaitingPayment();
        order.pullDomainEvents();

        order.markPaymentRejected("declined", NOW.plusSeconds(3));

        assertThat(order.status()).isEqualTo(OrderStatus.CANCELLATION_PENDING);
        assertThat(order.pullDomainEvents()).anyMatch(InventoryReleaseRequested.class::isInstance);
    }

    /** Verifica que repetir una notificación correcta ya aplicada no realice ninguna operación ni produzca efectos secundarios. */
    @Test
    void shouldSafelyIgnoreDuplicateSuccessfulTransition() {
        Order order = orderAwaitingPayment();
        order.markPaymentAuthorized(NOW.plusSeconds(3));
        order.pullDomainEvents();

        order.markPaymentAuthorized(NOW.plusSeconds(4));

        assertThat(order.status()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(order.pullDomainEvents()).isEmpty();
    }

    /** @return agregado válido que ha avanzado hasta {@link OrderStatus#PAYMENT_PENDING} */
    private Order orderAwaitingPayment() {
        Order order = newOrder(List.of(line("A", 1, "10.00")));
        order.requestInventoryReservation(NOW.plusSeconds(1));
        order.markInventoryReserved(NOW.plusSeconds(2));
        order.requestPaymentAuthorization(NOW.plusSeconds(2));
        return order;
    }

    /** Crea un agregado de prueba válido con identidades aleatorias y un instante fijo. */
    private Order newOrder(List<OrderLine> lines) {
        return Order.create(new OrderId(UUID.randomUUID()), new CustomerId(UUID.randomUUID()), lines,
                new PaymentMethodId("pm-test"), NOW);
    }

    /** Crea una línea de prueba concisa y completamente validada. */
    private OrderLine line(String product, int quantity, String price) {
        return new OrderLine(new ProductId(product), new Quantity(quantity), Money.eur(new BigDecimal(price)));
    }
}
