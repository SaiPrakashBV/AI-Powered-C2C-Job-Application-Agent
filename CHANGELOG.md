# Changelog

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
