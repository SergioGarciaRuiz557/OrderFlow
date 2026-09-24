-- Tabla raíz del agregado. Almacena todos los campos necesarios para reanudar el ciclo de vida tras un reinicio.
CREATE TABLE orders (
    -- UUID asignado por el dominio; a diferencia de los ID de línea, es visible entre servicios.
    id UUID PRIMARY KEY,
    -- Referencia externa del cliente. El servicio de pedidos no es propietario de los datos del cliente.
    customer_id UUID NOT NULL,
    -- Nombre del enum OrderStatus. El código de dominio, no un trigger de base de datos, controla las transiciones.
    status VARCHAR(50) NOT NULL,
    -- Referencia opaca de pago conservada hasta la etapa de pago de la saga.
    payment_method_id VARCHAR(255) NOT NULL,
    -- Total de dominio desnormalizado. La rehidratación lo recalcula y compara con las líneas.
    total NUMERIC(19, 2) NOT NULL CHECK (total >= 0),
    -- Código ISO 4217; la primera versión del dominio solo acepta EUR.
    currency VARCHAR(3) NOT NULL,
    -- Las fechas de creación y última transición permiten la recuperación y trazabilidad operativa.
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    -- @Version de Hibernate incrementa este valor y rechaza actualizaciones concurrentes obsoletas.
    version BIGINT NOT NULL DEFAULT 0
);

-- Líneas que pertenecen al agregado. Se eliminan automáticamente junto con su pedido propietario.
CREATE TABLE order_lines (
    -- Clave interna de persistencia sin significado para el dominio ni la API.
    id BIGSERIAL PRIMARY KEY,
    -- La integridad referencial garantiza que una línea no pueda existir fuera de un agregado Order.
    order_id UUID NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    -- Referencia externa de catálogo y condiciones de compra inmutables capturadas al crear el pedido.
    product_id VARCHAR(255) NOT NULL,
    quantity INTEGER NOT NULL CHECK (quantity > 0),
    unit_price NUMERIC(19, 2) NOT NULL CHECK (unit_price >= 0),
    currency VARCHAR(3) NOT NULL
);

-- Acelera la rehidratación del agregado, que siempre carga las líneas mediante su pedido propietario.
CREATE INDEX idx_order_lines_order_id ON order_lines(order_id);
-- Permite futuras consultas de pedidos por cliente sin cambiar la forma inicial del esquema.
CREATE INDEX idx_orders_customer_id ON orders(customer_id);
