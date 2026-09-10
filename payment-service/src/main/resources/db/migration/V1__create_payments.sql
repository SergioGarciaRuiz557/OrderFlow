CREATE TABLE payments (
    payment_id UUID PRIMARY KEY,
    order_id VARCHAR(100) NOT NULL,
    amount NUMERIC(19, 2) NOT NULL CHECK (amount >= 0),
    currency VARCHAR(3) NOT NULL CHECK (currency = 'EUR'),
    payment_method_id VARCHAR(200) NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN ('PENDING', 'AUTHORIZED', 'REJECTED')),
    provider_reference VARCHAR(200),
    failure_reason VARCHAR(200),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_payments_order UNIQUE (order_id),
    CONSTRAINT uk_payments_provider_reference UNIQUE (provider_reference),
    CONSTRAINT ck_payments_lifecycle CHECK (
        (status = 'PENDING' AND provider_reference IS NULL AND failure_reason IS NULL)
        OR (status = 'AUTHORIZED' AND provider_reference IS NOT NULL AND failure_reason IS NULL)
        OR (status = 'REJECTED' AND provider_reference IS NULL AND failure_reason IS NOT NULL)
    ),
    CONSTRAINT ck_payments_timestamps CHECK (updated_at >= created_at)
);
