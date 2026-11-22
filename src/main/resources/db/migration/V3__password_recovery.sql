ALTER TABLE app_user ADD COLUMN must_change_password BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE password_reset_token (
    id          UUID PRIMARY KEY,
    user_id     UUID NOT NULL REFERENCES app_user (id),
    token_hash  VARCHAR(64) NOT NULL UNIQUE,
    expires_at  TIMESTAMPTZ NOT NULL,
    used_at     TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL,
    updated_at  TIMESTAMPTZ NOT NULL,
    version     BIGINT NOT NULL
);
CREATE INDEX idx_password_reset_token_user ON password_reset_token (user_id);
CREATE INDEX idx_password_reset_token_expiry ON password_reset_token (expires_at);
