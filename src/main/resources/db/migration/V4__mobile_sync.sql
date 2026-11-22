CREATE TABLE sync_operation (
    id              UUID PRIMARY KEY,
    operation_id    UUID NOT NULL,
    user_id         UUID NOT NULL REFERENCES app_user (id),
    device_id       UUID NOT NULL,
    type            VARCHAR(50) NOT NULL,
    entity_id       UUID NOT NULL,
    base_version    BIGINT,
    depends_on      TEXT,
    payload         TEXT NOT NULL,
    status          VARCHAR(30) NOT NULL,
    result_code     VARCHAR(80),
    result_payload  TEXT,
    attempts        INTEGER NOT NULL,
    processed_at    TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL,
    updated_at      TIMESTAMPTZ NOT NULL,
    version         BIGINT NOT NULL,
    CONSTRAINT uk_sync_operation_identity UNIQUE (user_id, device_id, operation_id)
);
CREATE INDEX idx_sync_operation_processed ON sync_operation (processed_at);

CREATE TABLE sync_change (
    sequence        BIGSERIAL PRIMARY KEY,
    user_id         UUID NOT NULL REFERENCES app_user (id),
    entity_type     VARCHAR(50) NOT NULL,
    entity_id       UUID NOT NULL,
    operation_id    UUID,
    payload         TEXT NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_sync_change_user_sequence ON sync_change (user_id, sequence);
