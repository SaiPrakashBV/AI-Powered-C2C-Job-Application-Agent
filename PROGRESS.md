# PROGRESS

**Current state:** Phase 2 (Gmail Integration) complete; verified against stubs/fakes in the Linux cloud session.
**Next:** Phase 3 — Classification & Extraction (start only when the owner says so).
**Branch:** `claude/phase-1-foundation` (Phases 1–2; not yet merged to `main`).

## Phase status
| Phase | Scope | Status |
|---|---|---|
| 1 | Foundation: repo, Spring Boot, React, PostgreSQL, Flyway, auth, dashboard, Windows scripts | ✅ done (Windows scripts not yet run on Windows) |
| 2 | Gmail: OAuth, connect/disconnect, sync, message storage, attachment parsing, sender rules | ✅ done (not yet run against real Google) |
| 3 | Classification & extraction | ⬜ next |
| 4 | Job search intelligence (profiles, taxonomy, relevance, eligibility, duplicates) | ⬜ |
| 5 | Resume intelligence (library, master profile, ATS) | ⬜ |
| 6 | AI integration (gateway, Ollama, Claude, OpenAI, handoff) | ⬜ |
| 7 | Resume tailoring | ⬜ |
| 8 | Application management (templates, composer, approval, Gmail send, tracking) | ⬜ |
| 9 | Feedback & automation | ⬜ |
| 10 | Final hardening (E2E, launcher, backup/restore, security review, docs, packaging) | ⬜ |

## Phase 2 — delivered (2026-11-10)
- **Gmail OAuth** (`gmail/GmailOAuthService`, `GmailTokenService`): desktop flow, PKCE S256, loopback redirect
  `http://127.0.0.1:<port>/api/v1/gmail/oauth/callback`, single-use 10-min state (callback works without the
  SameSite=Strict session cookie), loopback-only return address, `access_type=offline&prompt=consent`, scope check,
  token refresh with 60 s margin, `invalid_grant` → REAUTH_REQUIRED, revoke on disconnect. Scopes:
  `gmail.readonly gmail.compose` (configurable `app.gmail.scopes`). Client from `C2C_GMAIL_CLIENT_FILE` or id/secret.
- **Token encryption** (`configuration/SecretCipher`): AES-256-GCM, key file `<data-dir>/keys/secrets.key`.
- **Sync** (`GmailSyncService`, `GMAIL_SYNC` job, `GmailSyncScheduler`): backfill N days (setting, default 14),
  incremental history.list, expired-checkpoint re-scan, pagination, `max-messages-per-sync` cap (PARTIAL status),
  transient retry/backoff, own-sent exclusion, polling every `gmailPollingIntervalMinutes`, resumes after restart.
- **Gmail REST client** (`GmailRestMailboxClient`, java.net.http, no Google SDK) behind `MailboxClient` interface.
- **Parsing** (`email/MimeEmailParser`, `AttachmentTextExtractor`, `SafeFilenames`): Jakarta Mail (angus-mail 2.0.5),
  jsoup 1.24.1 sanitization, PDFBox 3.0.8, POI 6.0.0 (zip-bomb limits); limits in `app.email.*`.
- **Ingestion** (`EmailIngestionService`): idempotent, source decision + explanation stored, bodies/attachments kept
  only for ALLOWED/DISCOVERY mail, attachments under `<data-dir>/attachments/<emailId>/`, path-traversal guard.
- **Source rules** (`SourceRuleEngine`, `SourceRuleService`, `/api/v1/source-rules`): CRUD, validation/normalization,
  per-account scope, priority, enable/disable, import/export JSON, evaluate endpoint, discovery-mode setting, audit.
- **APIs:** `/api/v1/gmail/{oauth/status,oauth/start,oauth/callback,accounts,accounts/{id}/sync|sync-enabled|disconnect}`,
  `/api/v1/emails` (search/filter/paginate), `/api/v1/emails/{id}`, attachment download (always as attachment).
- **UI:** Gmail Accounts (connect/reconnect/sync now/pause/disconnect with confirmation, OAuth error banners,
  backfill + polling settings), Source & Vendor Rules (add, toggle, delete, filter, import/export, test a sender,
  discovery mode, precedence explanation), Inbox (search/filter, detail with attachments + warnings).
- Migration `V2__gmail_and_email.sql`; `GMAIL_OAUTH_SETUP.md`; `ModuleCatalog`: gmail/source-rules AVAILABLE, inbox PARTIAL.

## Phase 2 — verification (cloud Linux container, PostgreSQL 16.15, OpenJDK 21.0.12)
- Backend `mvn test`: **62/62 pass** — new: SourceRuleEngineTest 10, MimeEmailParserTest 11, SecretCipherTest 2,
  GmailSyncIT 8 (FakeMailbox), GmailOAuthIT 7 (StubGoogleServer for token + Gmail REST), SourceRuleApiIT 3.
- Frontend: typecheck clean, Vitest **9/9**. Playwright E2E **3/3** against the packaged jar on a fresh DB.
- Manual check on the running jar: forged OAuth state → 302 to `/gmail?oauthError=invalid_state`; APIs still 401.
- Bug caught by Hibernate schema validation during the phase: `sha256 CHAR(64)` vs entity VARCHAR (fixed before release).

## Phase 2 — not verified / limitations
- **Not run against real Google**: developers.google.com and Google APIs are not reachable from the cloud session.
  Endpoints/scopes are Google's standard values and configurable. First owner action: follow GMAIL_OAUTH_SETUP.md,
  connect, sync, and report any error banner / `lastError` text.
- Profile-specific source rules (`profile_id`) are stored but only applied once profiles exist (Phase 4).
- Excluded (blocked/unlisted/own-sent) emails store headers + snippet only; changing rules does not re-fetch them yet
  (re-process action planned with Phase 3).
- No demo mailbox yet (tests use FakeMailbox); a labelled demo-mode fixture mailbox is planned for Phase 3 so the
  classifier can be tried without Gmail.
- OCR for scanned PDFs is not supported (flagged NEEDS_REVIEW). Legacy .doc/.rtf → NEEDS_REVIEW.
- Nested message/rfc822 attachments are inlined as forwarded text; their attachments are extracted too.

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

## Phase 3 plan (next session — Classification & Extraction)
1. Migration V3: `email_classification` (category, secondary intents, confidence, evidence jsonb, explanation,
   classifier, rule_version, is_current, corrected), `classification_pattern` / `technology_dictionary` (configurable
   rows seeded with defaults), `vendor`, `recruiter_contact`, `job_requirement` (+ `original_extraction` jsonb,
   field confidence, jd_index), `user_feedback` (minimal, for corrections).
2. Content preparation: body selection (text vs HTML-text), quoted-reply/signature splitting, forwarded-block
   detection, attachment text inclusion, multi-JD segmentation.
3. Deterministic `EmailClassifier` (interface) with configurable patterns: 10 categories, multi-intent, confidence,
   evidence spans, explanation; low confidence → UNCERTAIN → review queue. Thread context (In-Reply-To/References,
   existing thread) to tell vendor replies quoting old JDs from new JDs in a thread.
4. `CLASSIFY_EMAIL` job enqueued by ingestion for PENDING_CLASSIFICATION mail (dedup `classify:<emailId>`);
   only NEW_JOB_REQUIREMENT proceeds to extraction; replies/interviews are stored for Phase 8 linking.
5. JD + vendor extraction (`EXTRACT_REQUIREMENTS` job): title (generated when subject missing), skills mandatory vs
   preferred, location/remote, C2C/W2, rate, duration, client, recruiter name/email/phone (never invented),
   forwarder vs original recruiter, conflicting addresses flagged; field-level confidence + evidence; editable with
   original preserved.
6. UI: Inbox → classification review queue (filters by category/confidence, correct category = feedback),
   Extracted Jobs list/detail with editable fields and evidence highlighting; demo-mode fixture mailbox.
7. Tests: spec scenarios 1–17 as fixtures, ≥100 anonymized synthetic recruiter messages with precision/recall
   report per category; prompt-injection text in a JD treated as data (53).
