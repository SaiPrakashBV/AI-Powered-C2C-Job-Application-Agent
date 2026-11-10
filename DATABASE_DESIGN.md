# Database design

PostgreSQL 16, managed **only** by Flyway (`backend/src/main/resources/db/migration`). Hibernate validates the
schema (`ddl-auto: validate`). Tables are created in the phase that implements them, each in a new migration.

## Conventions
- Primary keys `UUID` (generated in Java). Mutable tables: `created_at`, `updated_at` (`TIMESTAMPTZ`), `version`
  (`BIGINT`, optimistic locking). Append-only tables (audit, executions, send attempts) have no `version`.
- Every user-data table has `workspace_id UUID NOT NULL REFERENCES workspace(id)` plus an index starting with it.
- Enums are `VARCHAR` + `CHECK` constraints (readable, easy to extend with a migration).
- **Original content and derived data are stored separately** (e.g. `incoming_email` raw parts vs
  `email_classification`; `job_requirement.original_extraction` vs current corrected values).
- Decision tables record `rule_version` / `prompt_version` / `model` so past decisions stay explainable.
- Large binaries (resume files, attachments) live in the data directory; tables store path, SHA-256, size, MIME.

## Implemented — V1 (Phase 1)
| Table | Purpose | Key constraints |
|---|---|---|
| `workspace` | Ownership boundary (one now, many later) | |
| `app_user` | Local accounts; bcrypt hash; lockout counters | unique `lower(username)`; role CHECK (OWNER, MEMBER) |
| `workspace_settings` | 1 row per workspace: `automation_mode` (default MANUAL_APPROVAL), `emergency_stop`, `outbound_dry_run` (default true), session timeout, polling interval, external-AI consent, theme | PK = `workspace_id`; CHECKs on ranges and enums |
| `audit_event` | Append-only audit trail (`actor_type` USER/SYSTEM/ANONYMOUS, `event_type`, entity ref, `summary`, `details jsonb` — never secrets/content) | index (`workspace_id`, `created_at desc`) |
| `background_job` | Durable queue: `job_type`, `status`, `payload jsonb`, `dedup_key`, `attempts`/`max_attempts`, `run_after`, lock owner/time, `last_error`, `failure_kind` | partial unique (`job_type`,`dedup_key`) WHERE status IN (QUEUED,RUNNING) → idempotent enqueue; index (`status`,`run_after`) |

## Implemented — V2 (Phase 2)
| Table | Purpose | Key constraints |
|---|---|---|
| `workspace_settings` (+cols) | `source_discovery_mode` (default false), `gmail_backfill_days` (default 14) | CHECK 1–365 |
| `gmail_account` | Mailbox: status (CONNECTED/REAUTH_REQUIRED/DISCONNECTED/ERROR), granted scopes, **encrypted** refresh/access tokens, sync flags, last sync result | unique (`workspace_id`, `lower(email_address)`) |
| `gmail_sync_checkpoint` | 1:1 per account: `history_id`, backfill window/completion, last incremental, messages synced | PK = account id, cascade delete |
| `incoming_email` | Original message: Gmail ids, labels, headers (subject **nullable**), text body, sanitized HTML + its text, snippet, `is_own_sent`, `source_decision` + rule id + explanation, `processing_status`, parse warnings | **unique (`gmail_account_id`, `gmail_message_id`)**; indexes by workspace/date, thread, status |
| `email_attachment` | Original + safe filename, MIME, size, SHA-256, storage path (relative to `<data-dir>/attachments`), `extraction_status` (EXTRACTED/UNSUPPORTED/TOO_LARGE/FAILED/NEEDS_REVIEW), extracted text, error | FK cascade from email |
| `email_source_rule` | Rule type (ALLOW/BLOCK ADDRESS/DOMAIN/LABEL, TRUSTED_FORWARDER), normalized value, optional Gmail account scope, `profile_id` (FK added in Phase 4), priority, enabled, note | unique (workspace, type, value, account, profile) |

Bodies and attachment files are stored only for messages that continue to classification (ALLOWED/DISCOVERY);
excluded messages keep headers + snippet only.

## Planned tables (entity → table, phase)
**Classification (P3)**
- `email_classification` — email, category (10 values), secondary intents (jsonb), confidence, evidence (jsonb),
  explanation, classifier (RULES/AI), rule_version, ai_execution_id, `is_current`, `corrected_by_user`.

**Jobs & vendors (P3–4)**
- `vendor` — company name, normalized name, domains. `recruiter_contact` — vendor, name, email, phone, source email.
- `job_requirement` — source email, index within email (multi-JD), internal title, all spec §M-6 fields (typed
  columns + jsonb lists), `field_confidence jsonb`, `original_extraction jsonb` (immutable), `status`,
  normalized keys for dedup (title, client, location, reference id, text fingerprint/simhash).
  Unique (`incoming_email_id`, `jd_index`).
- `job_search_profile` — name, target role, related titles, primary family, thresholds, preferences, enabled.
- `profile_technology_rule` — profile, technology, role (PRIMARY/MANDATORY/SUPPORTING/EXCLUDED), weight.
- `technology_taxonomy`, `technology_synonym` — configurable dictionary (Java ≠ JavaScript lives here + gate logic).
- `job_relevance_result` — requirement × profile, score breakdown (40/25/20/10/5), gate outcome, decision
  (SKIP/REVIEW/PROCEED), reasons, rule_version. Unique (requirement, profile, rule_version).
- `eligibility_decision` — requirement × profile, per-rule outcomes (PASS/FAIL/UNKNOWN), overall.
- `job_duplicate_group` + membership — similarity evidence, user policy decision.

**Resumes & scoring (P5)**
- `resume` — workspace, title, category, profile assignments (join table `resume_profile`), archived.
- `resume_version` — resume, version number, origin (UPLOAD/TAILORED/IMPORTED), file path, sha256, parsed text,
  sections jsonb, extracted technologies, parent version, verified flag. Unique (`resume_id`, `version_no`).
- `master_profile` (1 per workspace) and `verified_experience_fact` — type (EMPLOYER, PROJECT, SKILL, CERT,
  EDUCATION, METRIC…), structured value, dates, evidence reference, status (DRAFT/VERIFIED).
- `resume_match_result` — requirement × resume_version, total, recruiter-fit, missing mandatory skills, rule_version.
- `ats_score_breakdown` — match result, component (35/15/20/15/10/5), points, evidence mapping jsonb.

**AI (P6–7)**
- `ai_provider_configuration` — provider (CLAUDE_API, OPENAI_API, OLLAMA, CLAUDE_HANDOFF, CHATGPT_HANDOFF), model,
  endpoint, encrypted credential, limits, enabled; per-task routing table `ai_task_routing` (task → provider, fallback).
- `prompt_template` — key, version, body, output JSON schema, active. Unique (key, version).
- `ai_execution` — task, provider, model, prompt version, status, tokens, cost, latency, validation result, error.
- `tailoring_task` — requirement, base resume version, provider/handoff status steps, result version, validation
  report (unsupported claims, chronology), before/after scores, decision.

**Applications (P8–9)**
- `email_template` — type, subject/body templates, signature, version.
- `application_draft` — requirement, resume version, recipient, subject, body, thread/reply headers, status
  (DRAFT, READY_FOR_REVIEW, APPROVED, SENDING, SENT, FAILED, CANCELLED, UNCERTAIN_SEND), `version` lock.
- `application_approval` — draft, decision, user, timestamp, snapshot hash of what was approved.
- `job_application` — final record: gmail message id, recipient, resume version, sent at, status. **Unique
  (workspace, requirement)** unless the user policy explicitly allows a second application (stored as override).
- `email_send_attempt` — draft, idempotency key, attempt no, outcome (SENT/FAILED/TIMEOUT/RECONCILED), gmail id.
- `vendor_conversation_event` — application, incoming email, type (INTERVIEW, INFO_REQUEST, ACK, REJECTION…).
- `user_feedback` — target type/id, original decision + evidence, correction, final decision, provider/model,
  prompt & rule versions.
- `automation_policy` (versioned, explicit activation) and `automation_decision` (per draft: allowed/blocked + reasons).

## Uniqueness guarantees (spec §7)
Mailbox: (`workspace_id`, `lower(email_address)`). Message: (`gmail_account_id`, `gmail_message_id`). Requirement:
(`incoming_email_id`, `jd_index`). Resume version: (`resume_id`, `version_no`). Application: one per requirement
per workspace by default. Send attempts carry an idempotency key unique per draft.
