CREATE TABLE refresh_session (
    id          UUID PRIMARY KEY,
    jti         VARCHAR(80) NOT NULL UNIQUE,
    user_id     UUID NOT NULL REFERENCES app_user (id),
    expires_at  TIMESTAMPTZ NOT NULL,
    revoked_at  TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL,
    updated_at  TIMESTAMPTZ NOT NULL,
    version     BIGINT NOT NULL
);
CREATE INDEX idx_refresh_session_user ON refresh_session (user_id);
CREATE INDEX idx_refresh_session_expiry ON refresh_session (expires_at);
