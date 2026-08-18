CREATE SCHEMA IF NOT EXISTS notification;

SET search_path TO notification;

CREATE TABLE notifications (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    customer_id   UUID         NOT NULL,
    channel       VARCHAR(10)  NOT NULL CHECK (channel IN ('EMAIL', 'SMS', 'PUSH')),
    recipient     VARCHAR(255) NOT NULL,
    subject       VARCHAR(255),
    body          TEXT         NOT NULL,
    status        VARCHAR(20)  NOT NULL DEFAULT 'PENDING'
                      CHECK (status IN ('PENDING', 'SENT', 'FAILED', 'DEAD_LETTER')),
    retry_count   INT          NOT NULL DEFAULT 0,
    error_message VARCHAR(500),
    event_type    VARCHAR(100),
    sent_at       TIMESTAMP,
    created_at    TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE TABLE notification_preferences (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    customer_id     UUID      NOT NULL UNIQUE,
    email_enabled   BOOLEAN   NOT NULL DEFAULT true,
    sms_enabled     BOOLEAN   NOT NULL DEFAULT true,
    push_enabled    BOOLEAN   NOT NULL DEFAULT false,
    email_address   VARCHAR(255),
    mobile_number   VARCHAR(15),
    device_token    VARCHAR(255),
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_notifications_customer ON notifications (customer_id);
CREATE INDEX idx_notifications_status ON notifications (status, retry_count)
    WHERE status IN ('PENDING', 'FAILED');
CREATE INDEX idx_notifications_created_at ON notifications (created_at DESC);
