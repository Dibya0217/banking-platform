CREATE TABLE fraud_alerts (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    transaction_id      UUID NOT NULL,
    from_account_id     UUID NOT NULL,
    to_account_id       UUID,
    amount              NUMERIC(18, 2) NOT NULL,
    rule_name           VARCHAR(100) NOT NULL,
    severity            VARCHAR(20) NOT NULL CHECK (severity IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    status              VARCHAR(20) NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN', 'RESOLVED', 'FALSE_POSITIVE')),
    reason              VARCHAR(500) NOT NULL,
    resolved_by         UUID,
    resolved_at         TIMESTAMP,
    resolution_note     VARCHAR(500),
    created_at          TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_fraud_alerts_transaction      ON fraud_alerts(transaction_id);
CREATE INDEX idx_fraud_alerts_from_account     ON fraud_alerts(from_account_id);
CREATE INDEX idx_fraud_alerts_severity_status  ON fraud_alerts(severity, status);
CREATE INDEX idx_fraud_alerts_created_at       ON fraud_alerts(created_at);

CREATE TABLE blacklisted_accounts (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    account_id      UUID NOT NULL UNIQUE,
    reason          VARCHAR(500),
    blacklisted_by  UUID,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_blacklisted_accounts_account_id ON blacklisted_accounts(account_id);
