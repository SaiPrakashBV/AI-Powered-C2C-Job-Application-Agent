-- Phase 2: Gmail accounts, sync checkpoints, raw incoming email storage, attachments, source rules.
-- Original email content (headers, bodies, attachment files) is stored separately from later derived data
-- (classification/extraction tables arrive in Phase 3).

ALTER TABLE workspace_settings
    ADD COLUMN source_discovery_mode BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN gmail_backfill_days INT NOT NULL DEFAULT 14 CHECK (gmail_backfill_days BETWEEN 1 AND 365);

CREATE TABLE gmail_account (
    id                       UUID PRIMARY KEY,
    workspace_id             UUID         NOT NULL REFERENCES workspace (id),
    email_address            VARCHAR(320) NOT NULL,
    status                   VARCHAR(30)  NOT NULL
        CHECK (status IN ('CONNECTED', 'REAUTH_REQUIRED', 'DISCONNECTED', 'ERROR')),
    granted_scopes           VARCHAR(1000),
    refresh_token_encrypted  TEXT,
    access_token_encrypted   TEXT,
    access_token_expires_at  TIMESTAMPTZ,
    sync_enabled             BOOLEAN      NOT NULL DEFAULT TRUE,
    last_sync_at             TIMESTAMPTZ,
    last_sync_status         VARCHAR(30),
    last_error               VARCHAR(1000),
    connected_at             TIMESTAMPTZ,
    created_at               TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at               TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version                  BIGINT       NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX ux_gmail_account_ws_email ON gmail_account (workspace_id, lower(email_address));

CREATE TABLE gmail_sync_checkpoint (
    gmail_account_id      UUID PRIMARY KEY REFERENCES gmail_account (id) ON DELETE CASCADE,
    history_id            NUMERIC(30),
    backfill_completed_at TIMESTAMPTZ,
    backfill_from         TIMESTAMPTZ,
    last_incremental_at   TIMESTAMPTZ,
    messages_synced       BIGINT      NOT NULL DEFAULT 0,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    version               BIGINT      NOT NULL DEFAULT 0
);

CREATE TABLE incoming_email (
    id                   UUID PRIMARY KEY,
    workspace_id         UUID          NOT NULL REFERENCES workspace (id),
    gmail_account_id     UUID          NOT NULL REFERENCES gmail_account (id),
    gmail_message_id     VARCHAR(100)  NOT NULL,
    gmail_thread_id      VARCHAR(100),
    history_id           NUMERIC(30),
    label_ids            VARCHAR(2000),
    internal_date        TIMESTAMPTZ,
    from_address         VARCHAR(320),
    from_name            VARCHAR(500),
    reply_to             VARCHAR(1000),
    to_addresses         TEXT,
    cc_addresses         TEXT,
    subject              VARCHAR(2000),            -- may be NULL: subjects are optional
    message_id_header    VARCHAR(1000),
    in_reply_to          VARCHAR(1000),
    references_header    TEXT,
    raw_size_bytes       INT,
    text_body            TEXT,
    html_body_sanitized  TEXT,
    html_text            TEXT,
    snippet              VARCHAR(500),
    is_own_sent          BOOLEAN       NOT NULL DEFAULT FALSE,
    source_decision      VARCHAR(20)   NOT NULL
        CHECK (source_decision IN ('ALLOWED', 'BLOCKED', 'DISCOVERY', 'UNLISTED', 'OWN_SENT')),
    source_rule_id       UUID,
    source_explanation   VARCHAR(1000),
    processing_status    VARCHAR(30)   NOT NULL
        CHECK (processing_status IN ('PENDING_CLASSIFICATION', 'EXCLUDED', 'PARSE_ERROR', 'NEEDS_REVIEW',
                                     'CLASSIFIED')),
    parse_warnings       TEXT,
    created_at           TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ   NOT NULL DEFAULT now(),
    version              BIGINT        NOT NULL DEFAULT 0
);
-- Idempotent sync: one row per Gmail message per mailbox.
CREATE UNIQUE INDEX ux_incoming_email_account_msg ON incoming_email (gmail_account_id, gmail_message_id);
CREATE INDEX ix_incoming_email_ws_date ON incoming_email (workspace_id, internal_date DESC);
CREATE INDEX ix_incoming_email_thread ON incoming_email (gmail_account_id, gmail_thread_id);
CREATE INDEX ix_incoming_email_status ON incoming_email (workspace_id, processing_status);

CREATE TABLE email_attachment (
    id                 UUID PRIMARY KEY,
    workspace_id       UUID          NOT NULL REFERENCES workspace (id),
    incoming_email_id  UUID          NOT NULL REFERENCES incoming_email (id) ON DELETE CASCADE,
    original_filename  VARCHAR(500),
    safe_filename      VARCHAR(255)  NOT NULL,
    content_type       VARCHAR(200),
    size_bytes         INT           NOT NULL,
    sha256             VARCHAR(64),
    storage_path       VARCHAR(1000),
    is_inline          BOOLEAN       NOT NULL DEFAULT FALSE,
    extraction_status  VARCHAR(20)   NOT NULL
        CHECK (extraction_status IN ('EXTRACTED', 'UNSUPPORTED', 'TOO_LARGE', 'FAILED', 'NEEDS_REVIEW')),
    extracted_text     TEXT,
    extraction_error   VARCHAR(1000),
    created_at         TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ   NOT NULL DEFAULT now(),
    version            BIGINT        NOT NULL DEFAULT 0
);
CREATE INDEX ix_email_attachment_email ON email_attachment (incoming_email_id);

CREATE TABLE email_source_rule (
    id                UUID PRIMARY KEY,
    workspace_id      UUID          NOT NULL REFERENCES workspace (id),
    rule_type         VARCHAR(30)   NOT NULL
        CHECK (rule_type IN ('ALLOW_ADDRESS', 'ALLOW_DOMAIN', 'BLOCK_ADDRESS', 'BLOCK_DOMAIN',
                             'TRUSTED_FORWARDER', 'ALLOW_LABEL', 'BLOCK_LABEL')),
    match_value       VARCHAR(320)  NOT NULL,      -- normalized lower-case address, domain or label
    gmail_account_id  UUID          REFERENCES gmail_account (id) ON DELETE CASCADE,   -- NULL = all accounts
    profile_id        UUID,                        -- job-profile-specific rule; FK added with profiles (Phase 4)
    priority          INT           NOT NULL DEFAULT 100,
    enabled           BOOLEAN       NOT NULL DEFAULT TRUE,
    note              VARCHAR(500),
    created_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    version           BIGINT        NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX ux_email_source_rule ON email_source_rule
    (workspace_id, rule_type, match_value, COALESCE(gmail_account_id, '00000000-0000-0000-0000-000000000000'::uuid),
     COALESCE(profile_id, '00000000-0000-0000-0000-000000000000'::uuid));
CREATE INDEX ix_email_source_rule_ws ON email_source_rule (workspace_id, enabled);
