CREATE SCHEMA IF NOT EXISTS transaction;

SET search_path TO transaction;

CREATE TABLE transactions (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    from_account_id  UUID,
    to_account_id    UUID,
    transaction_type VARCHAR(20)    NOT NULL
                         CHECK (transaction_type IN ('DEPOSIT', 'WITHDRAWAL', 'TRANSFER', 'UPI_TRANSFER', 'REVERSAL')),
    amount           DECIMAL(18, 2) NOT NULL CHECK (amount > 0),
    currency         CHAR(3)        NOT NULL DEFAULT 'INR',
    status           VARCHAR(20)    NOT NULL DEFAULT 'PENDING'
                         CHECK (status IN ('PENDING', 'FRAUD_CHECKING', 'DEBIT_PENDING', 'CREDIT_PENDING',
                                           'COMPLETED', 'FAILED', 'REVERSED', 'MANUAL_REVIEW')),
    description      VARCHAR(255),
    idempotency_key  VARCHAR(64) UNIQUE,
    reference_number VARCHAR(50) UNIQUE,
    reversal_of      UUID REFERENCES transactions (id),
    failure_reason   VARCHAR(255),
    initiated_by     UUID           NOT NULL,
    ip_address       VARCHAR(45),
    channel          VARCHAR(20)    DEFAULT 'API'
                         CHECK (channel IN ('API', 'MOBILE', 'WEB', 'BRANCH', 'ATM')),
    created_at       TIMESTAMP      NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMP      NOT NULL DEFAULT NOW(),
    completed_at     TIMESTAMP
);

CREATE INDEX idx_transactions_from_account ON transactions (from_account_id);
CREATE INDEX idx_transactions_to_account ON transactions (to_account_id);
CREATE INDEX idx_transactions_status ON transactions (status);
CREATE INDEX idx_transactions_created_at ON transactions (created_at DESC);
CREATE INDEX idx_transactions_initiated_by ON transactions (initiated_by);

CREATE TABLE outbox_events (
    id             UUID PRIMARY KEY  DEFAULT gen_random_uuid(),
    topic          VARCHAR(100)  NOT NULL,
    aggregate_type VARCHAR(50)   NOT NULL,
    aggregate_id   UUID          NOT NULL,
    event_type     VARCHAR(100)  NOT NULL,
    payload        TEXT          NOT NULL,
    published      BOOLEAN       NOT NULL DEFAULT false,
    created_at     TIMESTAMP     NOT NULL DEFAULT NOW(),
    published_at   TIMESTAMP
);

CREATE INDEX idx_outbox_unpublished ON outbox_events (published, created_at) WHERE published = false;
