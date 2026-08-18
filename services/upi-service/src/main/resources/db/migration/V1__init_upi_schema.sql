CREATE SCHEMA IF NOT EXISTS upi;

SET search_path TO upi;

CREATE TABLE upi_ids (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    customer_id    UUID           NOT NULL,
    account_id     UUID           NOT NULL,
    vpa            VARCHAR(100)   NOT NULL UNIQUE,
    pin_hash       VARCHAR(255)   NOT NULL,
    daily_limit    DECIMAL(18, 2) NOT NULL DEFAULT 100000.00,
    status         VARCHAR(20)    NOT NULL DEFAULT 'ACTIVE'
                       CHECK (status IN ('ACTIVE', 'BLOCKED', 'DEACTIVATED')),
    created_at     TIMESTAMP      NOT NULL DEFAULT NOW(),
    deactivated_at TIMESTAMP
);

CREATE TABLE upi_transactions (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    upi_id         UUID           NOT NULL REFERENCES upi_ids (id),
    transaction_id UUID           NOT NULL,
    payer_vpa      VARCHAR(100)   NOT NULL,
    payee_vpa      VARCHAR(100)   NOT NULL,
    amount         DECIMAL(18, 2) NOT NULL,
    remarks        VARCHAR(255),
    status         VARCHAR(20)    NOT NULL,
    created_at     TIMESTAMP      NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_upi_ids_customer ON upi_ids (customer_id);
CREATE INDEX idx_upi_ids_vpa ON upi_ids (vpa);
CREATE INDEX idx_upi_transactions_upi_id ON upi_transactions (upi_id);
CREATE INDEX idx_upi_transactions_created_at ON upi_transactions (created_at DESC);
