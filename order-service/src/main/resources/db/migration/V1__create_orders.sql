-- Aggregate-root table. It stores every field required to resume the order lifecycle after restart.
CREATE TABLE orders (
    -- Domain-assigned UUID; unlike line IDs, it is visible across service boundaries.
    id UUID PRIMARY KEY,
    -- External customer reference. Order Service does not own customer data.
    customer_id UUID NOT NULL,
    -- OrderStatus enum name. Domain code, rather than a database trigger, controls transitions.
    status VARCHAR(50) NOT NULL,
    -- Opaque payment reference retained until the payment stage of the saga.
    payment_method_id VARCHAR(255) NOT NULL,
    -- Denormalized domain total. Rehydration recalculates and verifies it against line rows.
    total NUMERIC(19, 2) NOT NULL CHECK (total >= 0),
    -- ISO 4217 code; the first domain version accepts EUR only.
    currency VARCHAR(3) NOT NULL,
    -- Creation and latest-transition times provide recovery and operational traceability.
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    -- Hibernate @Version increments this value and rejects stale concurrent updates.
    version BIGINT NOT NULL DEFAULT 0
);

-- Aggregate-owned order lines. They are deleted automatically with their owning order.
CREATE TABLE order_lines (
    -- Internal persistence key with no domain or API meaning.
    id BIGSERIAL PRIMARY KEY,
    -- Referential integrity ensures a line cannot exist outside an Order aggregate.
    order_id UUID NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    -- External catalogue reference and the immutable purchase terms captured at creation.
    product_id VARCHAR(255) NOT NULL,
    quantity INTEGER NOT NULL CHECK (quantity > 0),
    unit_price NUMERIC(19, 2) NOT NULL CHECK (unit_price >= 0),
    currency VARCHAR(3) NOT NULL
);

-- Speeds aggregate rehydration, which always loads lines by owning order.
CREATE INDEX idx_order_lines_order_id ON order_lines(order_id);
-- Supports future customer-order queries without changing the initial schema shape.
CREATE INDEX idx_orders_customer_id ON orders(customer_id);
