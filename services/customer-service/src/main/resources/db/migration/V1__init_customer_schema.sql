CREATE TABLE customers (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    full_name       VARCHAR(100) NOT NULL,
    email           VARCHAR(150) NOT NULL UNIQUE,
    mobile          VARCHAR(15)  NOT NULL UNIQUE,
    date_of_birth   DATE,
    gender          VARCHAR(10),
    address_line1   VARCHAR(255),
    address_line2   VARCHAR(255),
    city            VARCHAR(100),
    state           VARCHAR(100),
    pincode         VARCHAR(10),
    pan_number      VARCHAR(10)  UNIQUE,
    aadhaar_number  VARCHAR(12)  UNIQUE,
    status          VARCHAR(30)  NOT NULL DEFAULT 'PENDING_VERIFICATION'
                    CHECK (status IN ('PENDING_VERIFICATION','PENDING_KYC','ACTIVE','FROZEN','CLOSED','KYC_REJECTED')),
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE customers IS 'Core customer identity table. No PII deletion — anonymization on account closure.';
COMMENT ON COLUMN customers.aadhaar_number IS 'Last 4 digits stored masked; full number encrypted separately.';

CREATE INDEX idx_customers_email  ON customers(email);
CREATE INDEX idx_customers_mobile ON customers(mobile);
CREATE INDEX idx_customers_pan    ON customers(pan_number);
CREATE INDEX idx_customers_status ON customers(status);

CREATE TABLE customer_kyc (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    customer_id       UUID NOT NULL REFERENCES customers(id),
    document_type     VARCHAR(50)  NOT NULL CHECK (document_type IN ('AADHAAR','PAN','PASSPORT','VOTER_ID','DRIVING_LICENSE')),
    document_number   VARCHAR(50)  NOT NULL,
    document_url      VARCHAR(500) NOT NULL,
    status            VARCHAR(20)  NOT NULL DEFAULT 'PENDING'
                      CHECK (status IN ('PENDING','APPROVED','REJECTED')),
    rejection_reason  VARCHAR(500),
    reviewed_at       TIMESTAMP,
    reviewed_by       UUID,
    created_at        TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_customer_kyc_customer_id ON customer_kyc(customer_id);
CREATE INDEX idx_customer_kyc_status      ON customer_kyc(status);
