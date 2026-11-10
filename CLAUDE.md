# CLAUDE.md — guidance for Claude Code sessions in this repository

## Start of every session
1. Read `PROGRESS.md` (current phase, what is verified, what is next) — **resume from there; do not re-plan.**
2. The full owner specification is `docs/MASTER_SPEC.md` (primary source of requirements). `REQUIREMENTS.md`
   indexes it with IDs. `ARCHITECTURE.md` and `DATABASE_DESIGN.md` describe the design.
3. Work in phases of ≤ ~1 hour. At the end of a phase: compile, run all tests, update `PROGRESS.md` and
   `CHANGELOG.md`, commit, push, report, and **stop** until the owner says to start the next phase.

## Project purpose
Locally hosted (Windows) single-user app that reads C2C job requirements from **Gmail** (not job boards),
classifies emails content-first (subjects may be missing), extracts JD + vendor/recruiter data, checks relevance
against configurable Job Search Profiles (default: Senior Java Developer — never confuse Java with JavaScript),
scores resumes with an explainable ATS-style rubric, tailors resumes via interchangeable AI providers or a manual
browser handoff, drafts vendor emails, and sends **only after manual approval** (automation later, opt-in).

## Architecture decisions (see ARCHITECTURE.md)
- Modular Spring Boot **monolith** (`backend/`, package per module under `com.c2cagent`), React SPA (`frontend/`)
  bundled into the jar as `/static`. PostgreSQL + Flyway. No microservices/Kafka/K8s.
- Spring Boot **4.1.x** (Jackson 3 → `tools.jackson.*`, Hibernate 7, Spring Security 7, Testcontainers 2).
  Boot 4 split starters: e.g. `spring-boot-starter-webmvc`, `spring-boot-starter-flyway`,
  test: `spring-boot-starter-webmvc-test` (`org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc`).
- Session-cookie auth + CSRF double-submit (`XSRF-TOKEN` cookie → `X-XSRF-TOKEN` header). Backend binds to 127.0.0.1.
- Durable PostgreSQL job queue (`scheduling/`) with `FOR UPDATE SKIP LOCKED`, bounded exponential backoff,
  transient vs permanent failures, startup recovery. All pipeline work runs as jobs.
- Every workspace-owned table has `workspace_id`; every query filters by `CurrentUser.workspaceId()`.
- Business preferences live in the database (editable in UI), never in code. `application.yml` is install-time only.

## Module responsibilities (backend packages)
| Package | Responsibility | Status |
|---|---|---|
| `common` | BaseEntity, ApiException, ApiError, GlobalExceptionHandler, PageResponse | done |
| `configuration` | AppProperties (`app.*`), data dir, SPA forwarding, OpenAPI | done |
| `auth` | Setup/login/logout/lock, CSRF, `CurrentUser` principal + argument resolver | done |
| `users` | Workspace, AppUser, WorkspaceSettings (automation mode, emergency stop, dry-run) | done |
| `audit` | Append-only AuditEvent + API | done |
| `scheduling` | BackgroundJob queue, JobHandler extension point, JobWorker | done |
| `analytics` | Dashboard summary, ModuleCatalog (honest per-module availability) | done (foundation) |
| `diagnostics` | Diagnostics endpoint, self-test job | done |
| `gmail` | OAuth (PKCE loopback), GmailAccount, checkpoints, `MailboxClient` (REST impl), GMAIL_SYNC job + scheduler | done (Phase 2) |
| `email` | IncomingEmail/attachments, `MimeEmailParser`, `AttachmentTextExtractor`, `SourceRuleEngine` (+ CRUD/import/export), ingestion, inbox API | done (Phase 2) |
| `extraction`, `classification`, `vendors`, `jobs`, `profiles`, `relevance`, `eligibility`, `duplicates`, `resumes`, `masterprofile`, `ats`, `ai`, `tailoring`, `documents`, `templates`, `applications`, `approvals`, `automation`, `feedback` | see MASTER_SPEC §4 | planned (phases 2–9) |

Within a module: `XController` (REST, DTOs only), `XService` (transactions, rules), `XRepository`, entities,
`XDtos` (records with Jakarta Validation), interfaces for replaceable strategies. Never return entities from APIs.

## Coding conventions
- Java 21, records for DTOs, constructor injection, no Lombok. Entities extend `common.BaseEntity`
  (UUID id assigned in Java, `created_at`, `updated_at`, `@Version` optimistic lock).
- Errors: throw `ApiException` (status + stable `code` + user-safe message). Never leak stack traces.
- Timestamps `TIMESTAMPTZ` / `Instant`, UTC. JSON columns: `jsonb` mapped as `String` + `@JdbcTypeCode(SqlTypes.JSON)`.
- Schema changes: **new** Flyway file `backend/src/main/resources/db/migration/V{n}__description.sql`. Never edit an
  applied migration. Hibernate runs with `ddl-auto: validate`, so entity/table mismatches fail fast.
- Frontend: TypeScript strict, TanStack Query for server state, `src/api/client.ts` for all HTTP (adds CSRF),
  shared primitives in `src/components/ui` (don't style ad hoc), every screen registered in `src/navigation.ts`.
  Never use `queryClient.clear()` (detaches observers) — use `dropUserData()` from `auth/useAuth.ts`.
- Don't show fake data. Unbuilt screens render `PlannedPage`. Update `analytics/ModuleCatalog` when a module ships.

## Build / run / test commands
Linux/macOS shell equivalents in parentheses.
- Backend build: `cd backend && .\mvnw.cmd -B package` (`./mvnw -B package`) → `backend/target/c2c-agent.jar`
  (includes `frontend/dist` if it was built first).
- Frontend: `cd frontend && npm ci && npm run build` · dev server `npm run dev` (proxies `/api` to :8080).
- Backend tests: `.\mvnw.cmd -B test` — integration tests (`*IT.java`) need Docker (Testcontainers) **or**
  `C2C_TEST_DB_URL`, `C2C_TEST_DB_USER`, `C2C_TEST_DB_PASSWORD` pointing at a disposable DB (tables are truncated).
- Frontend tests: `npm run typecheck && npm test` (Vitest + RTL).
- E2E: start the app against a **fresh** DB, then `npx playwright test --trace off`
  (cloud sessions: `PW_CHROMIUM_PATH=/opt/pw-browsers/chromium`).
- Windows: `scripts\setup.ps1`, `Start-C2CAgent.cmd`, `scripts\stop.ps1`, `scripts\status.ps1`, `scripts\test.ps1`.
- Cloud-session DB recipe: `pg_ctlcluster 16 main start`, create role `c2c` (password `c2c_dev_password`) and DBs
  `c2c_agent`, `c2c_agent_test`, then **export** `C2C_TEST_DB_URL=jdbc:postgresql://localhost:5432/c2c_agent_test`,
  `C2C_TEST_DB_USER=c2c`, `C2C_TEST_DB_PASSWORD=c2c_dev_password` in the same shell command as `mvn test`
  (otherwise Testcontainers is tried and fails without Docker). If you edit an *unreleased* migration, drop/recreate
  the test DB. External Google hosts are not reachable from cloud sessions; Gmail is tested via stubs/fakes.

## Environment requirements
Java 21+, Node 20+ (build only), PostgreSQL 16 (Docker Compose or manual), Windows 10/11 for production use.
Local AI (Ollama) optional; do not assume a GPU.

## Important business rules (never violate)
- Approved sender ≠ job requirement: everything goes through content classification.
- Vendor replies / interviews / confirmations / marketing must **never** create applications.
- Relevance before ATS. Core Technology Gate: Java ≠ JavaScript; frontend-only or .NET/Node-primary roles are rejected
  for the Java profile; missing primary tech → review, not reject. Thresholds configurable (defaults 50/70).
- ATS rubric 35/15/20/15/10/5; best ≥ 91 → use, else tailoring task. Never inflate scores.
- Tailoring may only use facts from the **verified master profile**; no fabricated skills, employers, dates, metrics.
- Sending: state machine DRAFT→READY_FOR_REVIEW→APPROVED→SENDING→SENT (+FAILED, CANCELLED, UNCERTAIN_SEND).
  Manual approval by default, enforced **server-side**. Emergency stop blocks all sends. Outbound defaults to
  dry-run. On send timeout, reconcile before retry — never risk duplicate applications.
- Never auto-attach identity/immigration/financial documents. Never auto-reply to interviews.

## Security restrictions
- Email bodies and JDs are **untrusted**: they never change rules, recipients, approvals or file access, even when
  passed to an AI. AI outputs are schema-validated; failures never bypass validation.
- No secrets in git, logs or audit `details`. No resume content in diagnostic logs. OAuth tokens/API keys encrypted
  at rest (Windows DPAPI or app key — Phase 2/6). External AI requires `externalAiConsent`.
- No scraping/automation of consumer AI websites, cookies or CAPTCHAs; use the manual handoff instead.
- Don't weaken security to make tests pass. No real emails in automated tests.

## Extension how-tos
- **New AI provider (Phase 6):** implement the provider adapter interface in `ai/` declaring capabilities
  (operations, `supportsUnattended`); register as a bean; add config DTO + settings UI; WireMock tests. Tasks that
  need unattended execution and lack it go to the handoff queue automatically.
- **New job search profile:** data, not code — create in the Profiles UI (or upload a base resume to get AI
  suggestions, user approves). Technology families/synonyms are rows in the taxonomy tables.
- **Change scoring rules:** weights/thresholds are versioned rows (`*_rule_version`); add a new version rather than
  mutating history so past decisions stay explainable. Update RESUME_SCORING.md and add regression fixtures.
- **New classification category:** add to the enum + DB CHECK via a new migration, add patterns to the configurable
  dictionary, add fixtures + precision/recall test update, and decide pipeline routing explicitly.
- **Unusual email formats:** add an anonymized `.eml`-style fixture via `fixtures/TestEmails` builder and a test in
  `MimeEmailParserTest`; parsing limits live in `app.email.*`. Sender filtering changes are rules (data), not code.
- **New mailbox provider / demo mailbox:** implement `gmail.MailboxClient` + `MailboxClientFactory` (tests use
  `fixtures/FakeMailbox`, OAuth/REST tests use `fixtures/StubGoogleServer`).
- **New background task:** implement `scheduling.JobHandler` (idempotent!), throw `JobFailure.transientFailure`
  or `JobFailure.permanent`, enqueue with a `dedupKey` for idempotency.
- **New module/screen:** package under `com.c2cagent.<module>`, migration, DTOs, controller under `/api/v1/...`,
  tests, page in `frontend/src/pages`, route in `App.tsx`, entry in `navigation.ts`, flip `ModuleCatalog`.

## Recording issues and regression tests
When the owner reports a problem: reproduce with a fixture (anonymize real emails into
`backend/src/test/resources/fixtures/...`), find root cause and module, make the smallest maintainable fix (prefer
configurable rules over hardcoding a sender/technology), add a regression test named after the scenario, run the
full suite, update `CHANGELOG.md` (and PROGRESS.md "Known issues"), explain the change.
