# AI-Powered C2C Job Application Agent

A locally hosted Windows application that reads C2C job requirements from your **Gmail**, decides which emails are
real new requirements (even without a subject), checks them against your job search profiles (default: Senior
Java Developer), picks or tailors the best resume with explainable scoring, drafts a vendor email, and sends it
only after **your approval**.

> **Status:** Phase 1 of 10 (foundation) is complete: login, dashboard, settings, safety controls, background
> jobs, Windows scripts. Gmail and the job pipeline arrive in later phases — see [PROGRESS.md](PROGRESS.md).

## Quick start (Windows 10/11)
Prerequisites: Java 21 (e.g. Eclipse Temurin 21), Node.js 20+ (build only), Docker Desktop (or a manually
installed PostgreSQL 16). Details: [WINDOWS_SETUP.md](WINDOWS_SETUP.md).

```powershell
powershell -ExecutionPolicy Bypass -File scripts\setup.ps1
```

Then double-click **`Start-C2CAgent.cmd`**. Your browser opens http://localhost:8080, where you create your local
account the first time. Stop the app with `Stop-C2CAgent.cmd`, and check it with `scripts\status.ps1`.

## Development
| Task | Command |
|---|---|
| Database only | `scripts\dev-db.ps1` |
| Backend (hot restart via IDE or) | `scripts\dev-backend.ps1` → http://localhost:8080 |
| Frontend with hot reload | `scripts\dev-frontend.ps1` → http://localhost:5173 |
| All tests | `scripts\test.ps1` (see [TESTING.md](TESTING.md)) |
| API docs | http://localhost:8080/swagger-ui.html |

## Documentation
- [docs/MASTER_SPEC.md](docs/MASTER_SPEC.md): the full product specification
- [REQUIREMENTS.md](REQUIREMENTS.md): requirement IDs and status
- [ARCHITECTURE.md](ARCHITECTURE.md), [DATABASE_DESIGN.md](DATABASE_DESIGN.md)
- [PROGRESS.md](PROGRESS.md), [CHANGELOG.md](CHANGELOG.md)
- [CLAUDE.md](CLAUDE.md): guidance for Claude Code sessions

## Safety defaults
Manual approval is required for every application. Outbound email is in **dry-run** mode until you explicitly
configure it. A global **emergency stop** is in Settings. Data stays on your computer in
`%USERPROFILE%\C2CAgent\data`, and the app listens on `127.0.0.1` only.
