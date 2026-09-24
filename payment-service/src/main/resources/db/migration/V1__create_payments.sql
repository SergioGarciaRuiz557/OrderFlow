-- Crea la representación duradera de la raíz del agregado Payment.
-- Flyway aplica este archivo una vez y registra la versión 1 en su tabla de historial del esquema.
CREATE TABLE payments (
    -- Identidad técnica del agregado generada por la aplicación.
    payment_id UUID PRIMARY KEY,
    -- Clave de idempotencia de negocio: un pedido solo puede iniciar una operación de pago.
    order_id VARCHAR(100) NOT NULL,
    -- El almacenamiento decimal exacto refleja la política no negativa y de dos decimales de Money.
    amount NUMERIC(19, 2) NOT NULL CHECK (amount >= 0),
    -- La moneda es explícita aunque la primera versión solo admita EUR intencionadamente.
    currency VARCHAR(3) NOT NULL CHECK (currency = 'EUR'),
    -- Token opaco del proveedor; aquí nunca se conservan datos sin procesar de tarjetas o cuentas bancarias.
    payment_method_id VARCHAR(200) NOT NULL,
    -- El almacenamiento textual del enum evita acoplar el significado conservado a una posición ordinal.
    status VARCHAR(20) NOT NULL CHECK (status IN ('PENDING', 'AUTHORIZED', 'REJECTED')),
    -- Solo una autorización satisfactoria produce una referencia del proveedor.
    provider_reference VARCHAR(200),
    -- Solo un rechazo de negocio definitivo produce un motivo de fallo.
    failure_reason VARCHAR(200),
    -- TIMESTAMPTZ almacena instantes con independencia de la zona horaria de la sesión de la base de datos.
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    -- Hibernate incrementa este token de bloqueo optimista con cada actualización del agregado.
    version BIGINT NOT NULL DEFAULT 0,

    -- La idempotencia en el nivel de base de datos sigue siendo autoritativa ante carreras o escrituras ajenas a la aplicación.
    CONSTRAINT uk_payments_order UNIQUE (order_id),
    -- Una referencia de autorización del proveedor nunca debe identificar dos pagos locales.
    CONSTRAINT uk_payments_provider_reference UNIQUE (provider_reference),

    -- Refleja en el límite de persistencia el invariante entre campos del ciclo de vida de Payment.
    CONSTRAINT ck_payments_lifecycle CHECK (
        (status = 'PENDING' AND provider_reference IS NULL AND failure_reason IS NULL)
        OR (status = 'AUTHORIZED' AND provider_reference IS NOT NULL AND failure_reason IS NULL)
        OR (status = 'REJECTED' AND provider_reference IS NULL AND failure_reason IS NOT NULL)
    ),
    -- Evita historiales imposibles aunque se escriban datos fuera del modelo de dominio.
    CONSTRAINT ck_payments_timestamps CHECK (updated_at >= created_at)
);
