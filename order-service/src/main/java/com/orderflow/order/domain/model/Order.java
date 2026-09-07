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

/**
 * Aggregate root that owns the complete business consistency boundary of one order.
 *
 * <p>Callers cannot assign {@link OrderStatus} directly. They request business behaviors and this
 * class validates the current state, applies the transition, updates the modification time, and
 * records domain events. Lines and the calculated total are immutable after creation in this first
 * version of the bounded context.</p>
 *
 * <p>The aggregate contains no persistence or messaging code. {@link #rehydrate} reconstructs saved
 * state without pretending that historical transitions have just happened.</p>
 */
public final class Order {
    /** Stable aggregate identity. */
    private final OrderId id;
    /** Customer that owns the order. */
    private final CustomerId customerId;
    /** Immutable snapshot of products, quantities, and unit prices. */
    private final List<OrderLine> lines;
    /** Payment reference to use when the saga reaches authorization. */
    private final PaymentMethodId paymentMethodId;
    /** Domain-calculated sum of every line subtotal. */
    private final Money total;
    /** Time at which the aggregate was originally created. */
    private final Instant createdAt;
    /** Events produced since the application last pulled them for publication. */
    private final List<OrderDomainEvent> domainEvents = new ArrayList<>();
    /** Current lifecycle state; changed only by behavior methods in this class. */
    private OrderStatus status;
    /** Time of the latest accepted state transition. */
    private Instant updatedAt;
    /** Persistence version used by JPA optimistic locking; null before the first insert. */
    private Long version;

    /**
     * Central constructor shared by creation and rehydration after their specific validations.
     * It copies the line list so external callers cannot mutate aggregate contents.
     */
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

    /**
     * Creates a new pending aggregate and records that creation as a domain fact.
     *
     * <p>The total is always derived from the supplied lines rather than accepted from a caller.</p>
     *
     * @param id new aggregate identity
     * @param customerId customer placing the order
     * @param lines non-empty order lines
     * @param paymentMethodId payment reference for later authorization
     * @param now creation time supplied through the application clock port
     * @return new aggregate in {@link OrderStatus#PENDING}
     */
    public static Order create(OrderId id, CustomerId customerId, List<OrderLine> lines,
                               PaymentMethodId paymentMethodId, Instant now) {
        Money total = calculateTotal(lines);
        Order order = new Order(id, customerId, lines, paymentMethodId, OrderStatus.PENDING,
                total, now, now, null);
        order.domainEvents.add(new OrderCreated(id, now));
        return order;
    }

    /**
     * Reconstructs an aggregate from persistence without recording new domain events.
     *
     * <p>Rehydration independently recalculates the total and compares it with the persisted total.
     * A mismatch indicates corrupt or incompatible stored state and is rejected.</p>
     *
     * @param id persisted aggregate identity
     * @param customerId persisted customer identity
     * @param lines persisted lines
     * @param paymentMethodId persisted payment reference
     * @param status persisted lifecycle state
     * @param total persisted total, verified against the lines
     * @param createdAt original creation time
     * @param updatedAt latest transition time
     * @param version optimistic-lock version
     * @return fully reconstructed aggregate with no pending events
     */
    public static Order rehydrate(OrderId id, CustomerId customerId, List<OrderLine> lines,
                                  PaymentMethodId paymentMethodId, OrderStatus status, Money total,
                                  Instant createdAt, Instant updatedAt, Long version) {
        Money calculatedTotal = calculateTotal(lines);
        if (!calculatedTotal.equals(total)) {
            throw new DomainInvariantViolationException("Persisted order total does not match its lines");
        }
        return new Order(id, customerId, lines, paymentMethodId, status, total, createdAt, updatedAt, version);
    }

    /**
     * Sums every line subtotal using the currency of the first line.
     *
     * @param lines lines whose total is required
     * @return calculated immutable total
     * @throws DomainInvariantViolationException when no lines are supplied or currencies differ
     */
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

    /**
     * Starts inventory reservation for a newly created order.
     *
     * <p>A duplicate call while reservation is already pending is a no-op because it represents the
     * same business intent. Every other unexpected state is rejected.</p>
     *
     * @param now transition time
     */
    public void requestInventoryReservation(Instant now) {
        if (status == OrderStatus.INVENTORY_RESERVATION_PENDING) {
            return;
        }
        requireStatus(OrderStatus.PENDING, "Inventory reservation can only be requested for a pending order");
        transitionTo(OrderStatus.INVENTORY_RESERVATION_PENDING, now);
        domainEvents.add(new InventoryReservationRequested(id, now));
    }

    /**
     * Applies a successful inventory callback.
     *
     * <p>Later successful states are accepted as idempotent re-delivery. The method does not request
     * payment itself; that orchestration belongs to the inventory-result application service.</p>
     *
     * @param now transition time
     */
    public void markInventoryReserved(Instant now) {
        if (status == OrderStatus.INVENTORY_RESERVED || status == OrderStatus.PAYMENT_PENDING || status == OrderStatus.CONFIRMED) {
            return;
        }
        requireStatus(OrderStatus.INVENTORY_RESERVATION_PENDING, "Inventory can only be reserved while reservation is pending");
        transitionTo(OrderStatus.INVENTORY_RESERVED, now);
        domainEvents.add(new InventoryReserved(id, now));
    }

    /**
     * Applies a failed inventory callback and terminates the order as cancelled.
     *
     * @param reason external business reason, normalized to {@code unspecified} when blank
     * @param now transition time
     */
    public void markInventoryRejected(String reason, Instant now) {
        if (status == OrderStatus.CANCELLED) {
            return;
        }
        requireStatus(OrderStatus.INVENTORY_RESERVATION_PENDING, "Inventory can only be rejected while reservation is pending");
        transitionTo(OrderStatus.CANCELLED, now);
        domainEvents.add(new InventoryRejected(id, normalizedReason(reason), now));
        domainEvents.add(new OrderCancelled(id, normalizedReason(reason), now));
    }

    /**
     * Requests payment only after inventory is known to be reserved.
     *
     * <p>Calling the method again while payment is pending is idempotent. Calling it before inventory
     * reservation is explicitly rejected to protect the saga ordering invariant.</p>
     *
     * @param now transition time
     */
    public void requestPaymentAuthorization(Instant now) {
        if (status == OrderStatus.PAYMENT_PENDING) {
            return;
        }
        requireStatus(OrderStatus.INVENTORY_RESERVED, "Payment cannot be authorized before inventory reservation");
        transitionTo(OrderStatus.PAYMENT_PENDING, now);
        domainEvents.add(new PaymentAuthorizationRequested(id, now));
    }

    /**
     * Applies successful payment authorization and confirms the order.
     *
     * <p>Authorization and confirmation are emitted as separate facts so future integrations can
     * react at the appropriate semantic level. Re-delivery after confirmation is a no-op.</p>
     *
     * @param now transition time
     */
    public void markPaymentAuthorized(Instant now) {
        if (status == OrderStatus.CONFIRMED) {
            return;
        }
        requireStatus(OrderStatus.PAYMENT_PENDING, "Payment can only be authorized while payment is pending");
        transitionTo(OrderStatus.CONFIRMED, now);
        domainEvents.add(new PaymentAuthorized(id, now));
        domainEvents.add(new OrderConfirmed(id, now));
    }

    /**
     * Applies failed payment authorization and starts inventory compensation.
     *
     * <p>The order remains in {@link OrderStatus#CANCELLATION_PENDING} until a future flow confirms
     * compensation. An {@link InventoryReleaseRequested} event expresses the required action.</p>
     *
     * @param reason payment rejection reason, normalized when blank
     * @param now transition time
     */
    public void markPaymentRejected(String reason, Instant now) {
        if (status == OrderStatus.CANCELLATION_PENDING || status == OrderStatus.CANCELLED) {
            return;
        }
        requireStatus(OrderStatus.PAYMENT_PENDING, "Payment can only be rejected while payment is pending");
        transitionTo(OrderStatus.CANCELLATION_PENDING, now);
        domainEvents.add(new PaymentRejected(id, normalizedReason(reason), now));
        domainEvents.add(new InventoryReleaseRequested(id, now));
    }

    /**
     * Requests cancellation before the order has been confirmed.
     *
     * <p>If inventory may already be reserved, the method also records a compensation request.
     * Confirmed orders are deliberately rejected because cancelling them requires a different future
     * post-confirmation business process.</p>
     *
     * @param reason caller context for the request; this first version does not persist it, so the
     *               definitive reason must be supplied again when cancellation is confirmed
     * @param now transition time
     */
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

    /**
     * Completes a previously requested cancellation.
     *
     * @param reason final cancellation reason, normalized when blank
     * @param now transition time
     */
    public void confirmCancellation(String reason, Instant now) {
        if (status == OrderStatus.CANCELLED) {
            return;
        }
        requireStatus(OrderStatus.CANCELLATION_PENDING, "Cancellation can only be confirmed while cancellation is pending");
        transitionTo(OrderStatus.CANCELLED, now);
        domainEvents.add(new OrderCancelled(id, normalizedReason(reason), now));
    }

    /**
     * Guards a transition that is valid from exactly one state.
     *
     * @param expected required current state
     * @param message business explanation used if the guard fails
     */
    private void requireStatus(OrderStatus expected, String message) {
        if (status != expected) {
            throw new DomainInvariantViolationException(message + "; current status is " + status);
        }
    }

    /** Assigns the validated next state and its transition time as one internal operation. */
    private void transitionTo(OrderStatus newStatus, Instant now) {
        status = newStatus;
        updatedAt = Objects.requireNonNull(now, "Transition time is required");
    }

    /** Converts absent or blank external reasons into a stable domain value. */
    private String normalizedReason(String reason) {
        return reason == null || reason.isBlank() ? "unspecified" : reason.trim();
    }

    /**
     * Returns all events produced since the previous pull and clears the internal buffer.
     *
     * <p>Clearing prevents the same aggregate instance from publishing an event twice. Application
     * services call this only after persistence succeeds.</p>
     *
     * @return immutable snapshot of currently pending events
     */
    public List<OrderDomainEvent> pullDomainEvents() {
        List<OrderDomainEvent> events = List.copyOf(domainEvents);
        domainEvents.clear();
        return events;
    }

    /**
     * Returns this aggregate's identity.
     *
     * @return aggregate identity
     */
    public OrderId id() { return id; }
    /**
     * Returns the owning customer.
     *
     * @return customer that owns the order
     */
    public CustomerId customerId() { return customerId; }
    /**
     * Returns the immutable purchase terms.
     *
     * @return immutable order-line snapshot
     */
    public List<OrderLine> lines() { return lines; }
    /**
     * Returns the future authorization reference.
     *
     * @return payment method reference
     */
    public PaymentMethodId paymentMethodId() { return paymentMethodId; }
    /**
     * Returns the domain-controlled state.
     *
     * @return current lifecycle state
     */
    public OrderStatus status() { return status; }
    /**
     * Returns the sum calculated from all lines.
     *
     * @return domain-calculated total
     */
    public Money total() { return total; }
    /**
     * Returns when the aggregate began.
     *
     * @return creation time
     */
    public Instant createdAt() { return createdAt; }
    /**
     * Returns when state last changed.
     *
     * @return time of the latest transition
     */
    public Instant updatedAt() { return updatedAt; }
    /**
     * Returns the concurrency token.
     *
     * @return persistence version, or null before the first save
     */
    public Long version() { return version; }
}
