-- Phase 1 foundation schema: workspaces, users, settings, audit trail, durable background jobs.
-- Later phases add their own tables in new versioned migrations (never edit an applied migration).
-- All business tables carry workspace_id for multi-user / multi-workspace isolation.

CREATE TABLE workspace (
    id          UUID PRIMARY KEY,
    name        VARCHAR(200) NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version     BIGINT       NOT NULL DEFAULT 0
);

CREATE TABLE app_user (
    id                UUID PRIMARY KEY,
    workspace_id      UUID         NOT NULL REFERENCES workspace (id),
    username          VARCHAR(100) NOT NULL,
    display_name      VARCHAR(200) NOT NULL,
    email             VARCHAR(320),
    password_hash     VARCHAR(255) NOT NULL,
    role              VARCHAR(30)  NOT NULL CHECK (role IN ('OWNER', 'MEMBER')),
    enabled           BOOLEAN      NOT NULL DEFAULT TRUE,
    failed_login_count INT         NOT NULL DEFAULT 0,
    locked_until      TIMESTAMPTZ,
    last_login_at     TIMESTAMPTZ,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version           BIGINT       NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX ux_app_user_username ON app_user (lower(username));
CREATE INDEX ix_app_user_workspace ON app_user (workspace_id);

-- One settings row per workspace. Typed columns for safety-critical values (automation, kill switch).
CREATE TABLE workspace_settings (
    workspace_id              UUID PRIMARY KEY REFERENCES workspace (id),
    automation_mode           VARCHAR(30) NOT NULL DEFAULT 'MANUAL_APPROVAL'
        CHECK (automation_mode IN ('MANUAL_APPROVAL', 'CONDITIONAL', 'RULES_BASED', 'FULL_AUTONOMOUS')),
    emergency_stop            BOOLEAN     NOT NULL DEFAULT FALSE,
    outbound_dry_run          BOOLEAN     NOT NULL DEFAULT TRUE,
    session_timeout_minutes   INT         NOT NULL DEFAULT 60 CHECK (session_timeout_minutes BETWEEN 5 AND 1440),
    gmail_polling_interval_minutes INT    NOT NULL DEFAULT 15 CHECK (gmail_polling_interval_minutes BETWEEN 1 AND 1440),
    external_ai_consent       BOOLEAN     NOT NULL DEFAULT FALSE,
    theme                     VARCHAR(10) NOT NULL DEFAULT 'system' CHECK (theme IN ('system', 'light', 'dark')),
    created_at                TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at                TIMESTAMPTZ NOT NULL DEFAULT now(),
    version                   BIGINT      NOT NULL DEFAULT 0
);

CREATE TABLE audit_event (
    id            UUID PRIMARY KEY,
    workspace_id  UUID         REFERENCES workspace (id),
    actor_user_id UUID         REFERENCES app_user (id),
    actor_type    VARCHAR(20)  NOT NULL CHECK (actor_type IN ('USER', 'SYSTEM', 'ANONYMOUS')),
    event_type    VARCHAR(80)  NOT NULL,
    entity_type   VARCHAR(80),
    entity_id     VARCHAR(100),
    summary       VARCHAR(500) NOT NULL,
    details       JSONB,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX ix_audit_event_ws_created ON audit_event (workspace_id, created_at DESC);
CREATE INDEX ix_audit_event_type ON audit_event (event_type);

-- Durable job queue (claimed with FOR UPDATE SKIP LOCKED). Survives restarts.
CREATE TABLE background_job (
    id              UUID PRIMARY KEY,
    workspace_id    UUID         REFERENCES workspace (id),
    job_type        VARCHAR(80)  NOT NULL,
    status          VARCHAR(20)  NOT NULL
        CHECK (status IN ('QUEUED', 'RUNNING', 'SUCCEEDED', 'FAILED', 'DEAD', 'CANCELLED')),
    payload         JSONB,
    dedup_key       VARCHAR(200),
    attempts        INT          NOT NULL DEFAULT 0,
    max_attempts    INT          NOT NULL DEFAULT 5 CHECK (max_attempts >= 1),
    run_after       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    locked_by       VARCHAR(100),
    locked_at       TIMESTAMPTZ,
    last_error      TEXT,
    failure_kind    VARCHAR(20) CHECK (failure_kind IN ('TRANSIENT', 'PERMANENT')),
    completed_at    TIMESTAMPTZ,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version         BIGINT       NOT NULL DEFAULT 0
);
CREATE INDEX ix_background_job_ready ON background_job (status, run_after);
CREATE INDEX ix_background_job_ws ON background_job (workspace_id, created_at DESC);
-- Idempotent enqueue: at most one active (queued/running) job per dedup key.
CREATE UNIQUE INDEX ux_background_job_active_dedup ON background_job (job_type, dedup_key)
    WHERE dedup_key IS NOT NULL AND status IN ('QUEUED', 'RUNNING');
