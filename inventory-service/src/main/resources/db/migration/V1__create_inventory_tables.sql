-- Almacena una raíz de agregado por producto. La cantidad disponible es el stock que todavía puede reservarse.
-- Hibernate incrementa la columna de versión, que evita que prevalezcan escrituras concurrentes obsoletas.
CREATE TABLE inventory_items (
    product_id VARCHAR(100) PRIMARY KEY,
    available_quantity INTEGER NOT NULL CHECK (available_quantity >= 0),
    version BIGINT NOT NULL DEFAULT 0
);

-- Almacena las entidades de reserva trazables que pertenecen a cada agregado de inventario.
CREATE TABLE stock_reservations (
    reservation_id UUID PRIMARY KEY,
    product_id VARCHAR(100) NOT NULL REFERENCES inventory_items(product_id) ON DELETE CASCADE,
    order_id VARCHAR(100) NOT NULL,
    quantity INTEGER NOT NULL CHECK (quantity > 0),
    status VARCHAR(20) NOT NULL CHECK (status IN ('ACTIVE', 'RELEASED')),
    reserved_at TIMESTAMPTZ NOT NULL,
    released_at TIMESTAMPTZ,
    -- El identificador del pedido es la clave de idempotencia de negocio dentro del agregado de un producto.
    CONSTRAINT uk_stock_reservation_product_order UNIQUE (product_id, order_id),
    -- El estado del ciclo de vida y la marca temporal de liberación deben describir siempre la misma transición.
    CONSTRAINT ck_stock_reservation_release_time CHECK (
        (status = 'ACTIVE' AND released_at IS NULL)
        OR (status = 'RELEASED' AND released_at IS NOT NULL)
    )
);

-- Permite cargar el agregado y recorrer la clave externa por producto sin examinar todas las reservas.
CREATE INDEX idx_stock_reservations_product_id ON stock_reservations(product_id);
