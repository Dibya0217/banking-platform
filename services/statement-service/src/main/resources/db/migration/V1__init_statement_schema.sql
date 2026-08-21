CREATE TABLE statements (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    account_id UUID NOT NULL,
    month INT NOT NULL,
    year INT NOT NULL,
    opening_balance NUMERIC(18,2),
    closing_balance NUMERIC(18,2),
    total_credits NUMERIC(18,2) DEFAULT 0,
    total_debits NUMERIC(18,2) DEFAULT 0,
    transaction_count INT DEFAULT 0,
    object_key VARCHAR(500),
    generated_at TIMESTAMP,
    status VARCHAR(20) DEFAULT 'PENDING' CHECK (status IN ('PENDING','GENERATED','FAILED')),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE(account_id, month, year)
);

CREATE TABLE statement_transactions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    account_id UUID NOT NULL,
    transaction_id UUID NOT NULL UNIQUE,
    transaction_type VARCHAR(20),
    amount NUMERIC(18,2) NOT NULL,
    direction VARCHAR(10) NOT NULL CHECK (direction IN ('CREDIT','DEBIT')),
    description VARCHAR(255),
    reference_number VARCHAR(50),
    transacted_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_stmt_txn_account_date ON statement_transactions(account_id, transacted_at);
