CREATE TABLE accounts (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    customer_id         UUID NOT NULL,
    account_number      VARCHAR(20)  NOT NULL UNIQUE,
    account_type        VARCHAR(20)  NOT NULL CHECK (account_type IN ('SAVINGS','CURRENT','FIXED_DEPOSIT')),
    balance             DECIMAL(18,2) NOT NULL DEFAULT 0.00 CHECK (balance >= 0),
    currency            CHAR(3)       NOT NULL DEFAULT 'INR',
    status              VARCHAR(20)   NOT NULL DEFAULT 'ACTIVE'
                        CHECK (status IN ('ACTIVE','FROZEN','CLOSED')),
    freeze_reason       VARCHAR(255),
    daily_debit_limit   DECIMAL(18,2) NOT NULL DEFAULT 100000.00,
    minimum_balance     DECIMAL(18,2) NOT NULL DEFAULT 1000.00,
    ifsc_code           VARCHAR(20)   NOT NULL DEFAULT 'BANK0000001',
    branch_code         VARCHAR(10),
    version             BIGINT        NOT NULL DEFAULT 0,
    created_at          TIMESTAMP     NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP     NOT NULL DEFAULT NOW(),
    closed_at           TIMESTAMP
);

CREATE INDEX idx_accounts_customer_id   ON accounts(customer_id);
CREATE INDEX idx_accounts_status        ON accounts(status);
CREATE INDEX idx_accounts_account_number ON accounts(account_number);

CREATE TABLE outbox_events (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    topic           VARCHAR(200) NOT NULL,
    aggregate_type  VARCHAR(100) NOT NULL,
    aggregate_id    UUID NOT NULL,
    event_type      VARCHAR(100) NOT NULL,
    payload         JSONB NOT NULL,
    published       BOOLEAN NOT NULL DEFAULT false,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    published_at    TIMESTAMP
);

CREATE INDEX idx_account_outbox_unpublished ON outbox_events(created_at) WHERE published = false;
