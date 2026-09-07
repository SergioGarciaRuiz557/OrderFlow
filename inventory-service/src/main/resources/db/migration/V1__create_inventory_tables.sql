-- Stores one aggregate root per product. Available quantity is the stock that can still be reserved.
-- The version column is incremented by Hibernate and prevents stale concurrent writes from winning.
CREATE TABLE inventory_items (
    product_id VARCHAR(100) PRIMARY KEY,
    available_quantity INTEGER NOT NULL CHECK (available_quantity >= 0),
    version BIGINT NOT NULL DEFAULT 0
);

-- Stores the traceable reservation entities owned by each inventory aggregate.
CREATE TABLE stock_reservations (
    reservation_id UUID PRIMARY KEY,
    product_id VARCHAR(100) NOT NULL REFERENCES inventory_items(product_id) ON DELETE CASCADE,
    order_id VARCHAR(100) NOT NULL,
    quantity INTEGER NOT NULL CHECK (quantity > 0),
    status VARCHAR(20) NOT NULL CHECK (status IN ('ACTIVE', 'RELEASED')),
    reserved_at TIMESTAMPTZ NOT NULL,
    released_at TIMESTAMPTZ,
    -- The order identifier is the business idempotency key within a product's aggregate.
    CONSTRAINT uk_stock_reservation_product_order UNIQUE (product_id, order_id),
    -- Lifecycle state and release timestamp must always describe the same transition state.
    CONSTRAINT ck_stock_reservation_release_time CHECK (
        (status = 'ACTIVE' AND released_at IS NULL)
        OR (status = 'RELEASED' AND released_at IS NOT NULL)
    )
);

-- Supports aggregate loading and foreign-key traversal by product without scanning all reservations.
CREATE INDEX idx_stock_reservations_product_id ON stock_reservations(product_id);
