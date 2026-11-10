# PROGRESS

**Current state:** Phase 1 (Project Foundation) complete and verified in the Linux cloud session.
**Next:** Phase 2 — Gmail Integration (start only when the owner says so).
**Branch:** `claude/phase-1-foundation` (not yet merged to `main`).

## Phase status
| Phase | Scope | Status |
|---|---|---|
| 1 | Foundation: repo, Spring Boot, React, PostgreSQL, Flyway, auth, dashboard, Windows scripts | ✅ done (Windows scripts not yet run on Windows) |
| 2 | Gmail: OAuth, connect/disconnect, sync, message storage, attachment parsing, sender rules | ⬜ next |
| 3 | Classification & extraction | ⬜ |
| 4 | Job search intelligence (profiles, taxonomy, relevance, eligibility, duplicates) | ⬜ |
| 5 | Resume intelligence (library, master profile, ATS) | ⬜ |
| 6 | AI integration (gateway, Ollama, Claude, OpenAI, handoff) | ⬜ |
| 7 | Resume tailoring | ⬜ |
| 8 | Application management (templates, composer, approval, Gmail send, tracking) | ⬜ |
| 9 | Feedback & automation | ⬜ |
| 10 | Final hardening (E2E, launcher, backup/restore, security review, docs, packaging) | ⬜ |

## Phase 1 — delivered (2026-11-10)
- Backend `backend/` (Spring Boot 4.1.2, Java 21, Maven wrapper): modules `common`, `configuration`, `auth`,
  `users`, `audit`, `scheduling`, `analytics`, `diagnostics`.
  - First-time setup (only once, advisory-locked), login with generic errors + lockout (5 tries / 15 min),
    logout/lock, session-id rotation, configurable session timeout, change password, bcrypt.
  - CSRF (SPA double-submit), CSP, frame deny, loopback bind, Actuator limited to health, JSON 401/403.
  - Settings: automation mode (fixed MANUAL_APPROVAL), emergency stop (audited), outbound dry-run (default on),
    polling interval, external-AI consent (audited), theme.
  - Durable job queue: idempotent enqueue (dedup key), SKIP LOCKED claim, backoff, FAILED/DEAD, manual retry/cancel,
    startup + periodic orphan recovery, `DIAGNOSTICS_SELF_TEST` handler.
  - Dashboard summary (real data only + module availability catalog), diagnostics, audit log API, OpenAPI at
    `/swagger-ui.html`, SPA forwarding for deep links.
  - Flyway `V1__foundation.sql`.
- Frontend `frontend/` (React 19, TS, Vite 8, Router 7, TanStack Query 5, Tailwind 4): setup, login, layout with
  sidebar for all 20 screens, dashboard, settings (safety + confirm modal, general, diagnostics, jobs table,
  audit log with filter + pagination), light/dark/system theme, skip link, honest "Phase N" pages.
- Windows: `Start-C2CAgent.cmd`, `Stop-C2CAgent.cmd`, `scripts/{setup,start,stop,status,dev-db,dev-backend,dev-frontend,test}.ps1`,
  `docker-compose.yml` (PostgreSQL 16, loopback), `.env.example`.
- Docs: CLAUDE.md, REQUIREMENTS.md, ARCHITECTURE.md, DATABASE_DESIGN.md, README.md, WINDOWS_SETUP.md, TESTING.md,
  CHANGELOG.md, docs/MASTER_SPEC.md.

## Verification performed (cloud Linux container, PostgreSQL 16.15, OpenJDK 21.0.12, Node 22)
- `mvn package`: 21 backend tests pass (AuthFlowIT 8, SettingsIT 3, DashboardIT 3, BackgroundJobIT 6, BackoffTest 1)
  against real PostgreSQL via `C2C_TEST_DB_URL` (Docker unavailable in the container, so the Testcontainers path
  was **not** exercised here).
- `npm run typecheck`, `npm test`: 5 Vitest tests pass; `npm run build` OK.
- Packaged jar started against a fresh DB; Playwright E2E (2 tests) passed: setup → dashboard → deep link →
  emergency stop engage/release with confirmation → self-test job SUCCEEDED → lock → wrong password → login.
- Bugs found and fixed during verification: SPA forward pattern swallowed `/assets/*.js`; `queryClient.clear()`
  detached observers so the UI did not advance after setup/login.

## Not verified / known limitations
- **PowerShell scripts and `.cmd` launchers have not been executed on Windows** (no PowerShell in the container;
  installing it was not permitted). First owner action: run `scripts\setup.ps1` and `Start-C2CAgent.cmd` and report errors.
- Testcontainers path untested here (needs Docker); `scripts\test.ps1` on Windows uses it by default.
- HTTP sessions are in memory: restarting the app logs you out.
- `stop.ps1` uses `Stop-Process` (not a graceful signal on Windows); interrupted jobs are re-queued on next start.
- Backup/restore scripts are Phase 10. Remaining docs (API_DOCUMENTATION, GMAIL_OAUTH_SETUP, AI_PROVIDER_SETUP,
  RESUME_SCORING, AUTOMATION_SAFETY, SECURITY, TROUBLESHOOTING, DEPLOYMENT, FUTURE_ENHANCEMENTS) are written in
  the phase that implements their subject.

## Phase 2 plan (next session — Gmail integration)
Owner prerequisites (can be done in parallel; code is testable without them):
Google Cloud project → enable Gmail API → OAuth consent screen (External, test user = your Gmail) → OAuth client
type **Desktop app** → download client JSON (keep out of git; path configured via `.env`).

1. Migration `V2__gmail_and_email.sql`: `gmail_account`, `gmail_sync_checkpoint`, `incoming_email`,
   `email_attachment`, `email_source_rule` (see DATABASE_DESIGN.md).
2. Secret storage: `SecretCipher` interface (AES-GCM with a key file in the data dir, Windows DPAPI-protected when
   available) for refresh tokens; never logged.
3. OAuth: PKCE + loopback redirect (`http://127.0.0.1:{random}/oauth2/callback` handled by backend), scopes
   `gmail.readonly`, `gmail.compose`, `gmail.send` (verify minimal set from Google docs), state param, token
   refresh, revoked → REAUTH_REQUIRED. Use google-api-services-gmail + google-auth-library (verify versions).
4. `MailboxClient` interface; Gmail implementation; **fixture implementation** for tests/demo mode.
5. Sync job: initial backfill (configurable days) + `history.list` incremental with checkpoint; on 404 expired
   history → bounded re-scan; pagination; rate-limit/backoff via JobFailure.transient; idempotent upsert by
   (account, message id); mark own sent messages (`SENT` label / from = account) as excluded.
6. MIME parsing (Jakarta Mail or Gmail payload parts): text/HTML (sanitize with jsoup Safelist), charsets, QP/Base64,
   forwarded blocks, attachments PDF/DOCX/TXT with size limits, sanitized filenames, zip-bomb guard; failed
   extraction → NEEDS_REVIEW (never silent).
7. Source rules: CRUD + import/export (CSV/JSON), explicit precedence engine with unit tests, discovery mode.
8. UI: Gmail Accounts (connect/disconnect/status/manual sync/backfill), Source & Vendor Rules, raw inbox list.
9. Tests: WireMock/fixture Gmail API, MIME fixtures (no subject, HTML-only, PDF/DOCX attachment, malformed
   attachment, duplicate history events), rule precedence; flip `ModuleCatalog` for gmail + source-rules.
