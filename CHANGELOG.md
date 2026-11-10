# Changelog

## [0.2.0] — 2026-11-10 — Phase 2: Gmail integration
### Added
- Gmail OAuth 2.0 desktop flow (PKCE S256, loopback redirect, single-use state, loopback-only return address);
  connect, reconnect, disconnect (revokes at Google), pause/resume sync, multiple accounts.
- AES-256-GCM encryption of OAuth tokens with a key file in the data directory.
- Durable `GMAIL_SYNC` job: initial backfill (configurable days), incremental `history.list`, expired-checkpoint
  re-scan, pagination, retry/backoff, `REAUTH_REQUIRED` on revoked access, idempotent per-message storage,
  own-sent exclusion, polling scheduler (configurable interval).
- MIME parsing (Jakarta Mail): multipart, QP/Base64, charsets, sanitized HTML + text, forwarded messages,
  size/part/depth limits, safe filenames; PDF/DOCX/TXT attachment text extraction with explicit
  NEEDS_REVIEW/FAILED/TOO_LARGE statuses; attachments stored under the data directory.
- Email source rules: approved/blocked addresses and domains, trusted forwarders, Gmail labels, per-account scope,
  priority, enable/disable, documented precedence, import/export (JSON), "test a sender", discovery mode.
- Screens: Gmail Accounts, Source & Vendor Rules, Inbox (synced mail with source decisions; classification in P3).
- Migration V2; GMAIL_OAUTH_SETUP.md.

## [0.1.0] — 2026-11-10 — Phase 1: Project foundation
### Added
- Spring Boot 4.1 backend: first-time setup, login/lockout, logout/lock, CSRF, CSP, workspace ownership, settings
  (manual-approval mode, emergency stop, dry-run, consent), audit log, durable PostgreSQL job queue with retries
  and restart recovery, diagnostics + self-test job, dashboard summary, OpenAPI.
- Flyway migration V1 (workspace, app_user, workspace_settings, audit_event, background_job).
- React 19 + TypeScript frontend: setup, login, sidebar layout for all planned screens, dashboard, settings and
  diagnostics, dark mode.
- Windows launcher (`Start-C2CAgent.cmd`, `Stop-C2CAgent.cmd`), PowerShell setup/start/stop/status/dev/test
  scripts, Docker Compose PostgreSQL, `.env.example`.
- Documentation: CLAUDE.md, REQUIREMENTS.md, ARCHITECTURE.md, DATABASE_DESIGN.md, PROGRESS.md, WINDOWS_SETUP.md,
  TESTING.md, docs/MASTER_SPEC.md.

### Fixed (found during verification)
- SPA route forwarding no longer intercepts `/assets/*` static files.
- The UI now advances after setup/login (React Query observers were being detached by `clear()`).
