CREATE SCHEMA IF NOT EXISTS beneficiary;

SET search_path TO beneficiary;

CREATE TABLE beneficiaries (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    customer_id          UUID          NOT NULL,
    account_number       VARCHAR(20)   NOT NULL,
    ifsc_code            VARCHAR(20)   NOT NULL,
    beneficiary_name     VARCHAR(100)  NOT NULL,
    bank_name            VARCHAR(100),
    nick_name            VARCHAR(50),
    status               VARCHAR(30)   NOT NULL DEFAULT 'PENDING_VERIFICATION'
                             CHECK (status IN ('PENDING_VERIFICATION', 'ACTIVE', 'REMOVED')),
    transfer_enabled_at  TIMESTAMP,
    penny_drop_txn_id    UUID,
    created_at           TIMESTAMP     NOT NULL DEFAULT NOW(),
    removed_at           TIMESTAMP,
    UNIQUE (customer_id, account_number, ifsc_code)
);

CREATE INDEX idx_beneficiaries_customer_id ON beneficiaries (customer_id);
CREATE INDEX idx_beneficiaries_status ON beneficiaries (status);
