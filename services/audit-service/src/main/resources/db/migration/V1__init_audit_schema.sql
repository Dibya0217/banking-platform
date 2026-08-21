CREATE TABLE audit_logs (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id        VARCHAR(100),
    event_type      VARCHAR(100) NOT NULL,
    topic           VARCHAR(100),
    actor_id        UUID,
    entity_type     VARCHAR(50),
    entity_id       VARCHAR(100),
    payload         TEXT,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_audit_event_type  ON audit_logs(event_type);
CREATE INDEX idx_audit_actor_id    ON audit_logs(actor_id);
CREATE INDEX idx_audit_entity      ON audit_logs(entity_type, entity_id);
CREATE INDEX idx_audit_created_at  ON audit_logs(created_at);
