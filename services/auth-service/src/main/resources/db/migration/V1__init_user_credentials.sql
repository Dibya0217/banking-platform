-- Auth schema is created by init-db.sql; Flyway manages objects within it

CREATE TABLE auth.user_credentials (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    customer_id     UUID         NOT NULL UNIQUE,
    email           VARCHAR(150) NOT NULL UNIQUE,
    mobile          VARCHAR(15)  NOT NULL UNIQUE,
    password_hash   VARCHAR(255) NOT NULL,
    failed_attempts INT          NOT NULL DEFAULT 0,
    locked_until    TIMESTAMPTZ,
    status          VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    version         BIGINT       NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_uc_email  ON auth.user_credentials(email);
CREATE INDEX idx_uc_mobile ON auth.user_credentials(mobile);
CREATE INDEX idx_uc_status ON auth.user_credentials(status);
