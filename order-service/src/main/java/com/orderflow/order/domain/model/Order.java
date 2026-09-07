package com.orderflow.order.domain.model;

import com.orderflow.order.domain.event.InventoryRejected;
import com.orderflow.order.domain.event.InventoryReleaseRequested;
import com.orderflow.order.domain.event.InventoryReservationRequested;
import com.orderflow.order.domain.event.InventoryReserved;
import com.orderflow.order.domain.event.OrderCancelled;
import com.orderflow.order.domain.event.OrderConfirmed;
import com.orderflow.order.domain.event.OrderCreated;
import com.orderflow.order.domain.event.OrderDomainEvent;
import com.orderflow.order.domain.event.PaymentAuthorizationRequested;
import com.orderflow.order.domain.event.PaymentAuthorized;
import com.orderflow.order.domain.event.PaymentRejected;
import com.orderflow.order.domain.exception.DomainInvariantViolationException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class Order {
    private final OrderId id;
    private final CustomerId customerId;
    private final List<OrderLine> lines;
    private final PaymentMethodId paymentMethodId;
    private final Money total;
    private final Instant createdAt;
    private final List<OrderDomainEvent> domainEvents = new ArrayList<>();
    private OrderStatus status;
    private Instant updatedAt;
    private Long version;

    private Order(OrderId id, CustomerId customerId, List<OrderLine> lines,
                  PaymentMethodId paymentMethodId, OrderStatus status, Money total,
                  Instant createdAt, Instant updatedAt, Long version) {
        this.id = Objects.requireNonNull(id, "Order id is required");
        this.customerId = Objects.requireNonNull(customerId, "Customer id is required");
        if (lines == null || lines.isEmpty()) {
            throw new DomainInvariantViolationException("An order must contain at least one line");
        }
        this.lines = List.copyOf(lines);
        this.paymentMethodId = Objects.requireNonNull(paymentMethodId, "Payment method id is required");
        this.status = Objects.requireNonNull(status, "Order status is required");
        this.total = Objects.requireNonNull(total, "Order total is required");
        this.createdAt = Objects.requireNonNull(createdAt, "Creation time is required");
        this.updatedAt = Objects.requireNonNull(updatedAt, "Update time is required");
        this.version = version;
    }

    public static Order create(OrderId id, CustomerId customerId, List<OrderLine> lines,
                               PaymentMethodId paymentMethodId, Instant now) {
        Money total = calculateTotal(lines);
        Order order = new Order(id, customerId, lines, paymentMethodId, OrderStatus.PENDING,
                total, now, now, null);
        order.domainEvents.add(new OrderCreated(id, now));
        return order;
    }

    public static Order rehydrate(OrderId id, CustomerId customerId, List<OrderLine> lines,
                                  PaymentMethodId paymentMethodId, OrderStatus status, Money total,
                                  Instant createdAt, Instant updatedAt, Long version) {
        Money calculatedTotal = calculateTotal(lines);
        if (!calculatedTotal.equals(total)) {
            throw new DomainInvariantViolationException("Persisted order total does not match its lines");
        }
        return new Order(id, customerId, lines, paymentMethodId, status, total, createdAt, updatedAt, version);
    }

    private static Money calculateTotal(List<OrderLine> lines) {
        if (lines == null || lines.isEmpty()) {
            throw new DomainInvariantViolationException("An order must contain at least one line");
        }
        Money total = Money.zero(lines.getFirst().unitPrice().currency());
        for (OrderLine line : lines) {
            total = total.add(line.subtotal());
        }
        return total;
    }

    public void requestInventoryReservation(Instant now) {
        if (status == OrderStatus.INVENTORY_RESERVATION_PENDING) {
            return;
        }
        requireStatus(OrderStatus.PENDING, "Inventory reservation can only be requested for a pending order");
        transitionTo(OrderStatus.INVENTORY_RESERVATION_PENDING, now);
        domainEvents.add(new InventoryReservationRequested(id, now));
    }

    public void markInventoryReserved(Instant now) {
        if (status == OrderStatus.INVENTORY_RESERVED || status == OrderStatus.PAYMENT_PENDING || status == OrderStatus.CONFIRMED) {
            return;
        }
        requireStatus(OrderStatus.INVENTORY_RESERVATION_PENDING, "Inventory can only be reserved while reservation is pending");
        transitionTo(OrderStatus.INVENTORY_RESERVED, now);
        domainEvents.add(new InventoryReserved(id, now));
    }

    public void markInventoryRejected(String reason, Instant now) {
        if (status == OrderStatus.CANCELLED) {
            return;
        }
        requireStatus(OrderStatus.INVENTORY_RESERVATION_PENDING, "Inventory can only be rejected while reservation is pending");
        transitionTo(OrderStatus.CANCELLED, now);
        domainEvents.add(new InventoryRejected(id, normalizedReason(reason), now));
        domainEvents.add(new OrderCancelled(id, normalizedReason(reason), now));
    }

    public void requestPaymentAuthorization(Instant now) {
        if (status == OrderStatus.PAYMENT_PENDING) {
            return;
        }
        requireStatus(OrderStatus.INVENTORY_RESERVED, "Payment cannot be authorized before inventory reservation");
        transitionTo(OrderStatus.PAYMENT_PENDING, now);
        domainEvents.add(new PaymentAuthorizationRequested(id, now));
    }

    public void markPaymentAuthorized(Instant now) {
        if (status == OrderStatus.CONFIRMED) {
            return;
        }
        requireStatus(OrderStatus.PAYMENT_PENDING, "Payment can only be authorized while payment is pending");
        transitionTo(OrderStatus.CONFIRMED, now);
        domainEvents.add(new PaymentAuthorized(id, now));
        domainEvents.add(new OrderConfirmed(id, now));
    }

    public void markPaymentRejected(String reason, Instant now) {
        if (status == OrderStatus.CANCELLATION_PENDING || status == OrderStatus.CANCELLED) {
            return;
        }
        requireStatus(OrderStatus.PAYMENT_PENDING, "Payment can only be rejected while payment is pending");
        transitionTo(OrderStatus.CANCELLATION_PENDING, now);
        domainEvents.add(new PaymentRejected(id, normalizedReason(reason), now));
        domainEvents.add(new InventoryReleaseRequested(id, now));
    }

    public void requestCancellation(String reason, Instant now) {
        if (status == OrderStatus.CANCELLATION_PENDING || status == OrderStatus.CANCELLED) {
            return;
        }
        if (status == OrderStatus.CONFIRMED) {
            throw new DomainInvariantViolationException("A confirmed order cannot be cancelled through this transition");
        }
        boolean inventoryWasReserved = status == OrderStatus.INVENTORY_RESERVED || status == OrderStatus.PAYMENT_PENDING;
        transitionTo(OrderStatus.CANCELLATION_PENDING, now);
        if (inventoryWasReserved) {
            domainEvents.add(new InventoryReleaseRequested(id, now));
        }
    }

    public void confirmCancellation(String reason, Instant now) {
        if (status == OrderStatus.CANCELLED) {
            return;
        }
        requireStatus(OrderStatus.CANCELLATION_PENDING, "Cancellation can only be confirmed while cancellation is pending");
        transitionTo(OrderStatus.CANCELLED, now);
        domainEvents.add(new OrderCancelled(id, normalizedReason(reason), now));
    }

    private void requireStatus(OrderStatus expected, String message) {
        if (status != expected) {
            throw new DomainInvariantViolationException(message + "; current status is " + status);
        }
    }

    private void transitionTo(OrderStatus newStatus, Instant now) {
        status = newStatus;
        updatedAt = Objects.requireNonNull(now, "Transition time is required");
    }

    private String normalizedReason(String reason) {
        return reason == null || reason.isBlank() ? "unspecified" : reason.trim();
    }

    public List<OrderDomainEvent> pullDomainEvents() {
        List<OrderDomainEvent> events = List.copyOf(domainEvents);
        domainEvents.clear();
        return events;
    }

    public OrderId id() { return id; }
    public CustomerId customerId() { return customerId; }
    public List<OrderLine> lines() { return lines; }
    public PaymentMethodId paymentMethodId() { return paymentMethodId; }
    public OrderStatus status() { return status; }
    public Money total() { return total; }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }
    public Long version() { return version; }
}
