package com.orderflow.order.domain.model;

/**
 * Persisted lifecycle state of an {@link Order}.
 *
 * <p>The enum describes possible states only. The aggregate methods decide which transitions are
 * valid and are the only production code allowed to change an order's state.</p>
 */
public enum OrderStatus {
    /** Aggregate exists but inventory has not yet been requested. */
    PENDING,
    /** An inventory reservation request is awaiting a result. */
    INVENTORY_RESERVATION_PENDING,
    /** Inventory is reserved but payment has not yet been requested. */
    INVENTORY_RESERVED,
    /** Payment authorization is awaiting a result. */
    PAYMENT_PENDING,
    /** Inventory and payment succeeded; this is a terminal successful state. */
    CONFIRMED,
    /** Compensation or cancellation work must complete before terminal cancellation. */
    CANCELLATION_PENDING,
    /** The order has been cancelled; this is a terminal state. */
    CANCELLED
}
