# Requirements index

The authoritative, complete specification is **[docs/MASTER_SPEC.md](docs/MASTER_SPEC.md)**. This file assigns
stable IDs so code, tests and PROGRESS.md can reference requirements. Status: ✅ verified · 🟡 partial · ⬜ not started.

## Owner clarifications (C-*)
| ID | Requirement | Status |
|---|---|---|
| C-1 | Gmail is the source of job requirements (~50–100 emails/day); no job-board scraping | ⬜ P2–3 |
| C-2 | Windows, Java 21, Spring Boot, React+TS, PostgreSQL, simple Windows launcher | 🟡 P1 (launcher written, not yet run on Windows) |
| C-3 | Gmail OAuth, multiple sender addresses/domains, content-based classification, no-subject emails | ⬜ P2–3 |
| C-4 | Configurable Job Search Profiles; default Senior Java; reject JS-only/React-only/.NET | ⬜ P4 |
| C-5 | Multiple resumes, verified master profile, explainable ATS, tailoring below threshold | ⬜ P5–7 |
| C-6 | Claude / OpenAI / Ollama adapters; manual browser handoff; no unsupported automation | ⬜ P6 |
| C-7 | Manual approval first; later conditional/automatic with limits and emergency stop | 🟡 P1 (mode + emergency stop + dry-run stored, enforced in P8) |
| C-8 | Multi-user-ready, single local user now | 🟡 P1 (workspace ownership + isolation tests) |
| C-9 | Full dashboard, approval queue, resumes, AI settings, source rules, history, feedback | 🟡 P1 (dashboard, settings) |
| C-10 | Nothing hardcoded that should be configurable; modular | ongoing |

## Modules (M-*) — spec §4
| ID | Module | Phase | Status |
|---|---|---|---|
| M-1 | Authentication & user management | 1 | ✅ setup, hashing, sessions, logout/lock, configurable timeout, lockout, ownership |
| M-2 | Gmail account integration (OAuth PKCE loopback, sync, checkpoints) | 2 | ⬜ |
| M-3 | Email source rules & vendor filters (explicit precedence) | 2 | ⬜ |
| M-4 | Content-first classification (10 categories, multi-intent, review queue) | 3 | ⬜ |
| M-5 | MIME content & attachment extraction (limits, safe parsing) | 2–3 | ⬜ |
| M-6 | JD & vendor extraction (field confidence, evidence, editable) | 3 | ⬜ |
| M-7 | Job search profiles (multiple, AI-suggested from resume, user-approved) | 4 | ⬜ |
| M-8 | Relevance engine (40/25/20/10/5, Core Technology Gate, 50/70 thresholds) | 4 | ⬜ |
| M-9 | Eligibility & duplicate detection | 4 | ⬜ |
| M-10 | Resume library (DOCX/PDF, versions, categories) | 5 | ⬜ |
| M-11 | Verified master professional profile | 5 | ⬜ |
| M-12 | ATS scoring (35/15/20/15/10/5, ≥91 select, explanations) | 5 | ⬜ |
| M-13 | AI provider gateway (capability-aware adapters) | 6 | ⬜ |
| M-14 | Browser-based AI handoff | 6 | ⬜ |
| M-15 | Resume tailoring & validation | 7 | ⬜ |
| M-16 | Email draft generation | 8 | ⬜ |
| M-17 | Approval & sending state machine, idempotent send | 8 (manual) / 9 (automation) | 🟡 settings foundation only |
| M-18 | Vendor reply & interview tracking | 8 | ⬜ |
| M-19 | Feedback & learning engine | 9 | ⬜ |
| M-20 | Tracking & analytics | 1 (shell) / 9 | 🟡 dashboard shows only real data |
| M-21 | Settings & admin controls | 1+ | 🟡 general settings, emergency stop, diagnostics, jobs, audit |

## Cross-cutting (X-*)
| ID | Requirement | Status |
|---|---|---|
| X-1 | Workflow §5 (32 steps) with persistent status per stage, resumable | 🟡 durable job queue + recovery |
| X-2 | 20 frontend screens §6 (sidebar, tables, filters, a11y, dark mode) | 🟡 shell + 3 screens; others show honest "Phase N" page |
| X-3 | Normalized schema §7 | 🟡 V1 foundation tables; full design in DATABASE_DESIGN.md |
| X-4 | REST `/api/v1`, pagination, ownership checks, OpenAPI §9 | 🟡 |
| X-5 | Windows scripts & launcher §10 | 🟡 written; not yet executed on Windows |
| X-6 | Security §11 | 🟡 auth, CSRF, CSP, loopback bind, audit, no secrets in repo |
| X-7 | Reliability §12 | 🟡 queue, backoff, dead-letter, recovery, graceful shutdown |
| X-8 | Test scenarios 1–60 §13 | ⬜ (54/55 partially: unauthorized access + cross-workspace isolation tested) |
| X-9 | Documentation set §14 | 🟡 see PROGRESS.md |
| X-10 | Acceptance criteria §17 (1–30) | 2 (login) verified; 1 (Windows launch) pending owner test |
