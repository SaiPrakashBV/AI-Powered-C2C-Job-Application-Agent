# Architecture

## Overview
A **modular Spring Boot monolith** with a React SPA, running locally on Windows and bound to `127.0.0.1:8080`.
PostgreSQL holds all state, including a durable job queue. Personal files live in a configurable data directory
(`%USERPROFILE%\C2CAgent\data` by default: `resumes/`, `attachments/`, `exports/`, `backups/`, `logs/`).

```
 Browser (React SPA, served by Spring Boot)
        │  HTTPS-less loopback, session cookie + XSRF header
        ▼
 ┌──────────────────────── Spring Boot (Java 21) ────────────────────────┐
 │ REST /api/v1 (controllers → DTOs)        Spring Security (session, CSRF)│
 │                                                                        │
 │  gmail ─► email(MIME) ─► classification ─► extraction ─► vendors       │
 │                │                 │                         │           │
 │                │        replies/interviews ──► applications(tracking)  │
 │                ▼                                                       │
 │  profiles ─► relevance ─► eligibility ─► duplicates ─► ats ◄─ resumes  │
 │                                              │             masterprofile│
 │                                              ▼                         │
 │                    tailoring ◄─► ai gateway (Claude | OpenAI | Ollama | │
 │                        │              manual browser handoff)          │
 │                        ▼                                               │
 │             templates/drafts ─► approvals ─► automation policy ─► send │
 │                                                                        │
 │ cross-cutting: auth · users/settings · audit · feedback · analytics ·  │
 │                scheduling (durable jobs) · configuration · diagnostics │
 └────────────────────────────────────┬───────────────────────────────────┘
                                      ▼
                          PostgreSQL 16 (Flyway-managed)
```

## Module boundaries
Each package under `com.c2cagent` owns its tables, entities, services and controllers. Modules talk to each
other through **service interfaces and IDs**, not by reaching into another module's repositories. Replaceable
strategies are interfaces:

| Extension point | Interface (phase) | Implementations |
|---|---|---|
| Background work | `scheduling.JobHandler` (P1) | self-test (P1); Gmail sync, classify, extract, score… |
| Mail provider | `gmail.MailboxClient` (P2) | Gmail API; fixture client for tests/demo |
| Classifier | `classification.EmailClassifier` (P3) | deterministic rules + dictionaries; AI-assisted |
| Relevance/ATS scoring | `relevance.RelevanceScorer`, `ats.ResumeScorer` (P4–5) | versioned rubric |
| AI provider | `ai.AiProvider` with `capabilities()` (P6) | Claude API, OpenAI API, Ollama, ManualHandoff |
| Document processing | `documents.DocumentTextExtractor` / `DocxWriter` (P2–7) | POI, PDFBox |
| Sender | `applications.OutboundSender` (P8) | Gmail send; dry-run sender (default) |

## Pipeline as durable jobs
Each workflow stage (spec §5) is a job type processing one entity and recording a status + explanation on that
entity. Stages enqueue the next stage with a dedup key (e.g. `classify:{incomingEmailId}`), so restarts, retries
and duplicate Gmail history events are idempotent. Jobs: `QUEUED → RUNNING → SUCCEEDED`; transient failure →
re-queued with bounded exponential backoff → `DEAD` when exhausted; permanent failure → `FAILED`. Both appear in
Settings → Background jobs for manual retry. On startup, RUNNING jobs from a previous process are re-queued.

## Security model
- Local owner account (bcrypt via `DelegatingPasswordEncoder`), lockout after 5 failures (configurable),
  session cookie `C2CSESSION` (HttpOnly, SameSite=Strict), session id rotated at login, timeout from settings.
- CSRF double-submit for the SPA; CSP `default-src 'self'`; frames denied; Actuator exposes only `health`.
- `CurrentUser` (userId, workspaceId) is the only way controllers know the caller; every query filters by
  workspace. Cross-workspace isolation has integration tests.
- Safety settings (automation mode, emergency stop, dry-run) are typed columns enforced server-side; changes audited.
- Untrusted content (emails, JDs) is data only — it can never modify rules, recipients, approvals or file access.

## Frontend
React 19 + TypeScript (strict) + Vite, React Router 7, TanStack Query 5, Tailwind 4 with an internal component
library (`src/components/ui`). `src/navigation.ts` lists all 20 spec screens; unbuilt ones render an honest
"scheduled for Phase N" page. Production build is copied into the jar (`backend/pom.xml` resource from
`frontend/dist`), so Node is not needed at runtime. Dev server on :5173 proxies `/api`.

## Versions (verified 2026-11-10 against Maven Central / npm)
Spring Boot 4.1.2 (Spring Security 7, Hibernate 7.4, Jackson 3.1, Flyway 12.4), springdoc 3.2.0,
Testcontainers 2.0.5, PostgreSQL driver 42.7.14; React 19.3, Vite 8.3, TypeScript 7.0, TanStack Query 5.107,
React Router 7.18, Tailwind 4.3, Vitest 5.1, Playwright 1.65.

## Key decisions
1. **Monolith over services** — one user, ~100 emails/day; simpler ops on Windows.
2. **PostgreSQL job queue instead of Quartz/JobRunr** — fewer dependencies, transactional with domain writes,
   `SKIP LOCKED` keeps it multi-instance safe for a future hosted version.
3. **Session cookies, not JWT** — same-origin SPA, easy revocation (lock/logout).
4. **In-memory HTTP sessions** — a restart logs the user out (acceptable locally); Spring Session JDBC can be
   added for hosted deployments.
5. **Manual browser handoff for consumer AI** — no scraping/automation of chat websites (spec §13–14).
