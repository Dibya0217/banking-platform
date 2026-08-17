CREATE TABLE auth.password_history (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    credential_id   UUID        NOT NULL REFERENCES auth.user_credentials(id) ON DELETE CASCADE,
    password_hash   VARCHAR(255) NOT NULL,
    changed_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_ph_credential_id ON auth.password_history(credential_id);
