-- FieldOps initial schema (spec section 10 - Modelo de Dados)

CREATE TABLE app_user (
    id              UUID PRIMARY KEY,
    name            VARCHAR(255) NOT NULL,
    email           VARCHAR(255) NOT NULL,
    password_hash   VARCHAR(255) NOT NULL,
    role            VARCHAR(20)  NOT NULL,
    status          VARCHAR(20)  NOT NULL,
    phone           VARCHAR(50),
    created_at      TIMESTAMPTZ NOT NULL,
    updated_at      TIMESTAMPTZ NOT NULL,
    version         BIGINT NOT NULL,
    CONSTRAINT uk_app_user_email UNIQUE (email)
);
CREATE INDEX idx_app_user_status ON app_user (status);
CREATE INDEX idx_app_user_role ON app_user (role);

CREATE TABLE client (
    id              UUID PRIMARY KEY,
    name            VARCHAR(255) NOT NULL,
    legal_name      VARCHAR(255),
    document        VARCHAR(50),
    email           VARCHAR(255),
    phone           VARCHAR(50),
    status          VARCHAR(20) NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL,
    updated_at      TIMESTAMPTZ NOT NULL,
    version         BIGINT NOT NULL
);
CREATE INDEX idx_client_status ON client (status);

CREATE TABLE inspection_site (
    id              UUID PRIMARY KEY,
    client_id       UUID NOT NULL REFERENCES client (id),
    name            VARCHAR(255) NOT NULL,
    description     VARCHAR(1000),
    address_line    VARCHAR(255),
    city            VARCHAR(120),
    state           VARCHAR(120),
    postal_code     VARCHAR(20),
    latitude        NUMERIC(9,6),
    longitude       NUMERIC(9,6),
    contact_name    VARCHAR(255),
    contact_phone   VARCHAR(50),
    status          VARCHAR(20) NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL,
    updated_at      TIMESTAMPTZ NOT NULL,
    version         BIGINT NOT NULL
);
CREATE INDEX idx_site_client ON inspection_site (client_id);
CREATE INDEX idx_site_status ON inspection_site (status);

CREATE TABLE equipment (
    id              UUID PRIMARY KEY,
    site_id         UUID NOT NULL REFERENCES inspection_site (id),
    name            VARCHAR(255) NOT NULL,
    asset_number    VARCHAR(120),
    serial_number   VARCHAR(120),
    manufacturer    VARCHAR(120),
    model           VARCHAR(120),
    description     VARCHAR(1000),
    qr_code         VARCHAR(255) NOT NULL,
    status          VARCHAR(20) NOT NULL,
    installed_at    DATE,
    created_at      TIMESTAMPTZ NOT NULL,
    updated_at      TIMESTAMPTZ NOT NULL,
    version         BIGINT NOT NULL,
    CONSTRAINT uk_equipment_qr_code UNIQUE (qr_code)
);
CREATE INDEX idx_equipment_site ON equipment (site_id);
CREATE INDEX idx_equipment_status ON equipment (status);
CREATE INDEX idx_equipment_asset_number ON equipment (asset_number);

CREATE TABLE inspection_template (
    id              UUID PRIMARY KEY,
    title           VARCHAR(255) NOT NULL,
    description     VARCHAR(2000),
    category        VARCHAR(120) NOT NULL,
    status          VARCHAR(20) NOT NULL,
    current_version INTEGER NOT NULL DEFAULT 0,
    created_by      UUID NOT NULL REFERENCES app_user (id),
    created_at      TIMESTAMPTZ NOT NULL,
    updated_at      TIMESTAMPTZ NOT NULL,
    version         BIGINT NOT NULL
);
CREATE INDEX idx_template_status ON inspection_template (status);
CREATE INDEX idx_template_category ON inspection_template (category);

CREATE TABLE inspection_template_version (
    id                          UUID PRIMARY KEY,
    template_id                 UUID NOT NULL REFERENCES inspection_template (id),
    version_number               INTEGER NOT NULL,
    title_snapshot               VARCHAR(255) NOT NULL,
    description_snapshot         VARCHAR(2000),
    published_by                 UUID REFERENCES app_user (id),
    published_at                 TIMESTAMPTZ,
    active_for_new_inspections   BOOLEAN NOT NULL DEFAULT FALSE,
    created_at                   TIMESTAMPTZ NOT NULL,
    updated_at                   TIMESTAMPTZ NOT NULL,
    version                      BIGINT NOT NULL,
    CONSTRAINT uk_template_version_number UNIQUE (template_id, version_number)
);
CREATE INDEX idx_template_version_template ON inspection_template_version (template_id);

CREATE TABLE template_section (
    id                    UUID PRIMARY KEY,
    template_version_id   UUID NOT NULL REFERENCES inspection_template_version (id),
    title                 VARCHAR(255) NOT NULL,
    description           VARCHAR(1000),
    display_order         INTEGER NOT NULL,
    created_at            TIMESTAMPTZ NOT NULL,
    updated_at            TIMESTAMPTZ NOT NULL,
    version               BIGINT NOT NULL
);
CREATE INDEX idx_section_version ON template_section (template_version_id);

CREATE TABLE template_item (
    id                                  UUID PRIMARY KEY,
    section_id                          UUID NOT NULL REFERENCES template_section (id),
    code                                VARCHAR(60),
    title                               VARCHAR(500) NOT NULL,
    description                         VARCHAR(2000),
    response_type                       VARCHAR(20) NOT NULL,
    required                            BOOLEAN NOT NULL DEFAULT TRUE,
    observation_required_on_failure     BOOLEAN NOT NULL DEFAULT FALSE,
    evidence_required_on_failure        BOOLEAN NOT NULL DEFAULT FALSE,
    options_json                        TEXT,
    display_order                       INTEGER NOT NULL,
    created_at                          TIMESTAMPTZ NOT NULL,
    updated_at                          TIMESTAMPTZ NOT NULL,
    version                             BIGINT NOT NULL
);
CREATE INDEX idx_item_section ON template_item (section_id);

CREATE TABLE inspection (
    id                      UUID PRIMARY KEY,
    template_version_id     UUID NOT NULL REFERENCES inspection_template_version (id),
    client_id               UUID NOT NULL REFERENCES client (id),
    site_id                 UUID NOT NULL REFERENCES inspection_site (id),
    equipment_id             UUID REFERENCES equipment (id),
    technician_id            UUID NOT NULL REFERENCES app_user (id),
    supervisor_id            UUID NOT NULL REFERENCES app_user (id),
    created_by               UUID NOT NULL REFERENCES app_user (id),
    title                    VARCHAR(255) NOT NULL,
    instructions             TEXT,
    priority                 VARCHAR(20) NOT NULL,
    status                   VARCHAR(20) NOT NULL,
    scheduled_for            TIMESTAMPTZ NOT NULL,
    started_at_device        TIMESTAMPTZ,
    started_at_server        TIMESTAMPTZ,
    completed_at_device      TIMESTAMPTZ,
    submitted_at_server      TIMESTAMPTZ,
    approved_at              TIMESTAMPTZ,
    canceled_at              TIMESTAMPTZ,
    canceled_by              UUID REFERENCES app_user (id),
    canceled_reason          VARCHAR(1000),
    created_at               TIMESTAMPTZ NOT NULL,
    updated_at               TIMESTAMPTZ NOT NULL,
    version                  BIGINT NOT NULL
);
CREATE INDEX idx_inspection_tech_status ON inspection (technician_id, status);
CREATE INDEX idx_inspection_supervisor_status ON inspection (supervisor_id, status);
CREATE INDEX idx_inspection_client ON inspection (client_id);
CREATE INDEX idx_inspection_site ON inspection (site_id);
CREATE INDEX idx_inspection_equipment ON inspection (equipment_id);
CREATE INDEX idx_inspection_scheduled_for ON inspection (scheduled_for);
CREATE INDEX idx_inspection_status_scheduled_for ON inspection (status, scheduled_for);

CREATE TABLE inspection_item_snapshot (
    id                          UUID PRIMARY KEY,
    inspection_id               UUID NOT NULL REFERENCES inspection (id),
    source_template_item_id      UUID,
    section_title                VARCHAR(255) NOT NULL,
    section_description          VARCHAR(1000),
    section_order                INTEGER NOT NULL,
    item_code                    VARCHAR(60),
    item_title                   VARCHAR(500) NOT NULL,
    item_description             VARCHAR(2000),
    response_type                VARCHAR(20) NOT NULL,
    required                     BOOLEAN NOT NULL,
    rules_json                   TEXT,
    options_json                 TEXT,
    item_order                   INTEGER NOT NULL,
    created_at                   TIMESTAMPTZ NOT NULL,
    updated_at                   TIMESTAMPTZ NOT NULL,
    version                      BIGINT NOT NULL
);
CREATE INDEX idx_snapshot_inspection ON inspection_item_snapshot (inspection_id);
CREATE INDEX idx_snapshot_inspection_order ON inspection_item_snapshot (inspection_id, section_order, item_order);

CREATE TABLE inspection_response (
    id                      UUID PRIMARY KEY,
    inspection_id           UUID NOT NULL REFERENCES inspection (id),
    inspection_item_id      UUID NOT NULL REFERENCES inspection_item_snapshot (id),
    value_text              TEXT,
    value_number            NUMERIC(18,4),
    value_boolean           BOOLEAN,
    value_date              DATE,
    value_json              TEXT,
    observation              TEXT,
    conformity                VARCHAR(20),
    answered_by               UUID REFERENCES app_user (id),
    answered_at_device        TIMESTAMPTZ,
    server_received_at        TIMESTAMPTZ,
    created_at                TIMESTAMPTZ NOT NULL,
    updated_at                TIMESTAMPTZ NOT NULL,
    version                   BIGINT NOT NULL,
    CONSTRAINT uk_response_item UNIQUE (inspection_item_id)
);
CREATE INDEX idx_response_inspection ON inspection_response (inspection_id);
CREATE INDEX idx_response_answered_by ON inspection_response (answered_by);

CREATE TABLE non_conformity (
    id                      UUID PRIMARY KEY,
    inspection_id           UUID NOT NULL REFERENCES inspection (id),
    inspection_item_id      UUID REFERENCES inspection_item_snapshot (id),
    response_id             UUID REFERENCES inspection_response (id),
    title                   VARCHAR(255) NOT NULL,
    description             TEXT,
    severity                VARCHAR(20) NOT NULL,
    status                  VARCHAR(20) NOT NULL,
    created_by              UUID NOT NULL REFERENCES app_user (id),
    created_at_device       TIMESTAMPTZ,
    server_received_at      TIMESTAMPTZ,
    created_at              TIMESTAMPTZ NOT NULL,
    updated_at              TIMESTAMPTZ NOT NULL,
    version                 BIGINT NOT NULL
);
CREATE INDEX idx_nc_inspection ON non_conformity (inspection_id);
CREATE INDEX idx_nc_severity ON non_conformity (severity);
CREATE INDEX idx_nc_status ON non_conformity (status);

CREATE TABLE evidence (
    id                      UUID PRIMARY KEY,
    inspection_id           UUID NOT NULL REFERENCES inspection (id),
    response_id             UUID REFERENCES inspection_response (id),
    non_conformity_id       UUID REFERENCES non_conformity (id),
    type                    VARCHAR(20) NOT NULL,
    storage_key             VARCHAR(500) NOT NULL,
    local_uri               VARCHAR(500),
    original_file_name      VARCHAR(255),
    mime_type               VARCHAR(120),
    size_bytes              BIGINT,
    checksum                VARCHAR(128),
    description             VARCHAR(1000),
    latitude                NUMERIC(9,6),
    longitude               NUMERIC(9,6),
    captured_at_device      TIMESTAMPTZ,
    server_received_at      TIMESTAMPTZ,
    uploaded_at              TIMESTAMPTZ,
    created_by               UUID NOT NULL REFERENCES app_user (id),
    created_at                TIMESTAMPTZ NOT NULL,
    updated_at                TIMESTAMPTZ NOT NULL,
    version                   BIGINT NOT NULL
);
CREATE INDEX idx_evidence_inspection ON evidence (inspection_id);
CREATE INDEX idx_evidence_response ON evidence (response_id);
CREATE INDEX idx_evidence_nc ON evidence (non_conformity_id);
CREATE INDEX idx_evidence_uploaded_at ON evidence (uploaded_at);

CREATE TABLE inspection_review (
    id                UUID PRIMARY KEY,
    inspection_id     UUID NOT NULL REFERENCES inspection (id),
    reviewer_id       UUID NOT NULL REFERENCES app_user (id),
    decision          VARCHAR(20) NOT NULL,
    reason            VARCHAR(1000),
    comments          TEXT,
    reviewed_at       TIMESTAMPTZ NOT NULL,
    review_cycle      INTEGER NOT NULL,
    created_at        TIMESTAMPTZ NOT NULL,
    updated_at        TIMESTAMPTZ NOT NULL,
    version           BIGINT NOT NULL
);
CREATE INDEX idx_review_inspection ON inspection_review (inspection_id);

CREATE TABLE audit_event (
    id                     UUID PRIMARY KEY,
    inspection_id          UUID,
    actor_id                UUID,
    action                  VARCHAR(60) NOT NULL,
    entity_type              VARCHAR(60) NOT NULL,
    entity_id                 UUID,
    occurred_at                TIMESTAMPTZ NOT NULL,
    device_occurred_at         TIMESTAMPTZ,
    previous_value_json         TEXT,
    new_value_json               TEXT,
    metadata_json                 TEXT,
    request_id                     VARCHAR(120),
    device_id                       VARCHAR(120)
);
CREATE INDEX idx_audit_entity ON audit_event (entity_type, entity_id);
CREATE INDEX idx_audit_inspection ON audit_event (inspection_id);
CREATE INDEX idx_audit_actor ON audit_event (actor_id);
CREATE INDEX idx_audit_occurred_at ON audit_event (occurred_at);
CREATE INDEX idx_audit_request_id ON audit_event (request_id);
