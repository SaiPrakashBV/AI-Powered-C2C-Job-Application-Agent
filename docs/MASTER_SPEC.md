# MASTER PROJECT SPECIFICATION — AI-Powered C2C Job Application Agent

> This is the owner's master specification, preserved verbatim in content (lightly reformatted) so every
> future Claude Code session has the full scope. **It is the primary source of requirements.**
> `REQUIREMENTS.md` indexes it with requirement IDs; if the two ever disagree, this file wins unless the
> owner states otherwise.
>
> Owner clarifications given alongside this spec (2026-11-10):
> 1. Not a job-board scraper. The primary source of job requirements is Gmail (~50–100 emails/day).
> 2. Windows, Java 21, Spring Boot, React, TypeScript, PostgreSQL; launch locally through a simple Windows launcher.
> 3. Gmail OAuth, configurable multiple sender addresses and domains, content-based classification, JD and vendor
>    extraction, identification of actual new job requirements. Emails may have no subject. Vendor replies,
>    interview notifications and unrelated emails must not trigger new applications.
> 4. Configurable Job Search Profiles. Initial target: Senior Java Developer and closely related Java roles.
>    Reject JavaScript-only, React-only, .NET and other unrelated roles. Future profiles via uploading different base resumes.
> 5. Multiple resumes, verified master professional profile, relevance scoring, explainable ATS-style scoring,
>    resume selection, AI-assisted tailoring when the best resume is below the configured threshold.
> 6. AI: Claude, OpenAI/ChatGPT, local (Ollama) via interchangeable adapters. API access optional. Manual browser
>    handoff for Claude/ChatGPT when no API. No unsupported browser automation.
> 7. Initially every application email requires approval. Later: conditional and fully automatic sending with
>    configurable rules, validation, limits and an emergency stop.
> 8. Future multi-user; first version for one local user.
> 9. Complete functional dashboard, approval queue, resume management, AI settings, source rules, history, feedback.
> 10. Nothing hardcoded that should be configurable; modular so unusual email patterns can be fixed without rewrites.

---

## 1. Role and primary objective

Act as Principal Software Architect, Senior Java Backend Engineer, Senior React Developer, AI/LLM Engineer,
Database Architect, Security Engineer and QA Automation Engineer.

Design, implement, test, document and package a complete, production-quality, locally hosted AI-powered C2C Job
Application Agent. It must be a real, working application — not static screens, mocked workflows, placeholder
implementations or diagrams alone.

Runs initially on the owner's Windows computer for one user; architecture must support future multi-user / cloud.
Must be modular, configurable, maintainable, secure and extensible.

**Primary objective:** automatically identify relevant C2C job requirements received through Gmail, evaluate their
compatibility with target roles and available resumes, intelligently select or tailor the best resume, draft a
professional vendor email, and either request approval or submit automatically based on configurable automation
settings. Initially every outgoing application requires manual approval. Full automation only when explicitly
enabled and validated. Do not hardcode business rules that should be configurable.

## 2. Technology stack

- **Backend:** Java 21, Spring Boot, Spring Security, Spring Data JPA/Hibernate, Spring Validation, Spring Scheduler
  or a durable background job framework, REST, Maven, OpenAPI/Swagger, Flyway.
- **Frontend:** React + TypeScript, Vite, React Router, TanStack Query, Tailwind CSS, consistent accessible UI
  component library, responsive dashboard with tables, filters, tabs, notifications, modals.
- **Database:** PostgreSQL, relational modeling with indexed FKs, versioned migrations, auditing, integrity constraints.
- **AI:** Anthropic Claude API adapter (optional), OpenAI API adapter (optional), Ollama local adapter, Claude
  browser manual handoff, ChatGPT browser manual handoff, provider-independent orchestration layer.
- **Documents:** Apache POI (DOCX), Apache PDFBox (text PDFs), HTML-to-text + sanitization, DOCX generation and
  templates, PDF export where supported.
- **Gmail:** official Gmail API, Google OAuth 2.0, background sync, draft creation and sending, attachments, threads.
- **Testing:** JUnit 5, Mockito, Spring Boot integration tests, Testcontainers (PostgreSQL), WireMock or equivalent,
  Vitest + React Testing Library, Playwright E2E.
- **Deployment:** Windows 10/11, Docker Compose for PostgreSQL in development, native Java backend on Windows,
  React production build served by Spring Boot, PowerShell install/start/stop/diagnostic scripts.

Use current stable compatible dependencies; verify versions from official sources. Prefer a modular Spring Boot
monolith. Avoid microservices, Kafka, Kubernetes, cloud infrastructure.

## 3. Core architectural principles (mandatory)

1. Separation of concerns between Gmail, classification, relevance, ATS scoring, AI, document generation, sending.
2. Provider-independent AI architecture.
3. Configuration-driven rules instead of hardcoded logic.
4. Modular backend with clear service interfaces.
5. Typed, validated request/response contracts.
6. Persistent processing state and recoverable background jobs.
7. Idempotent email synchronization and application submission.
8. Secure management of Gmail tokens and AI credentials.
9. Auditable decisions with explanations.
10. Feedback-based improvements without unsafe self-modifying behavior.
11. Multi-user-ready data ownership and isolation.
12. No mandatory paid AI API subscription.
13. Human approval by default.
14. Automatic sending explicitly enabled and never bypasses safety rules.
15. All functionality testable without real Gmail credentials or external AI services.

Easy to replace one AI provider, scoring strategy, email provider or document processor without rewriting unrelated
modules. SOLID and appropriate patterns, without unnecessary abstraction.

## 4. Required application modules

### Module 1 — Authentication and User Management
Single-user setup during first installation; secure password hashing; authenticated sessions; logout and application
locking; configurable session timeout; future multi-user; user-specific settings/resources; user/workspace ownership
in persistent records. No complex SaaS registration for first release.

### Module 2 — Gmail Account Integration
Google OAuth 2.0 connection UI. Connect/disconnect; multiple Gmail accounts; connection status; token renewal via
supported OAuth mechanisms; reconnect on revocation; secure storage of credentials/refresh tokens; never request or
store Gmail passwords; background retrieval; manual sync; configurable polling interval; initial backfill with
configurable date range; track message IDs, thread IDs, history IDs, sync checkpoints; recover after shutdown/restart;
handle pagination, rate limits, retries, expired history checkpoints; exclude the app's own sent messages from
new-requirement processing. Use desktop OAuth flow with PKCE + loopback redirect. Request only needed scopes
(read, compose, send). Periodic sync first; keep open for push notifications later.

### Module 3 — Email Source Rules and Vendor Filters
Exact sender addresses; approved domains; blocked addresses; blocked domains; trusted forwarding addresses; Gmail
labels; discovery mode for unknown recruiters; rules per Gmail account; global and profile-specific rules;
import/export; enable/disable and priority. An approved sender does NOT mean every message is a new requirement —
all approved messages still go through content classification. Define explicit, visible, testable precedence for
conflicting allow/block rules.

### Module 4 — Intelligent Email Classification
Content-first; never depend on subject. Handle: no subject, generic/incorrect/forwarded subjects, replies with newly
introduced requirements, JD only in body, JD only in attachments, multiple JDs per email. Analyze plain text,
sanitized HTML, supported attachments, forwarded content, recent thread context, headers, sender rules, technical
terminology, job intent.
Categories: NEW_JOB_REQUIREMENT, VENDOR_REPLY, INTERVIEW_NOTIFICATION, RECRUITER_FOLLOW_UP, SUBMISSION_CONFIRMATION,
JOB_STATUS_UPDATE, MARKETING_OR_NEWSLETTER, PERSONAL_OR_UNRELATED, AUTOMATED_RESPONSE, UNCERTAIN.
Support multiple intents. Only verified new requirements enter the pipeline. Replies/interviews linked to existing
applications, never trigger duplicate applications. Confidence, evidence, explanation. Low confidence → review queue.
Manual correction saves feedback. Deterministic baseline classifier with configurable patterns and technology
dictionaries; AI may perform/assist semantic classification when enabled.

### Module 5 — Email Content and Attachment Extraction
Text/HTML, multipart MIME, inline content, quoted-printable/Base64, charsets, forwarded messages, nested email
attachments where feasible, PDF/DOCX/TXT attachments, multiple JDs, missing/malformed subjects. Safe HTML
sanitization. File size limits; protect against malicious content, decompression abuse, unsafe filenames. On
extraction failure flag the attachment for review; never silently discard errors.

### Module 6 — JD and Vendor Data Extraction
Fields: job title, description, required experience, mandatory technologies, preferred technologies,
responsibilities, domain experience, client/end-client, vendor company, recruiter name/email/phone, location,
remote/hybrid/onsite, local-only restriction, contract type, C2C/W2/1099 eligibility, work authorization
restrictions, rate/salary, duration, interview mode, job reference ID, application instructions, original source
email, extraction confidence, missing/uncertain fields.
Separate JD from signatures; exclude unrelated employer details; identify correct application recipient;
distinguish original recruiters from forwarders; preserve original content; never invent contact info; flag
conflicting addresses; generate internal title if subject missing; every field manually editable; preserve original
extraction and corrections. Structured JSON contracts with field-level confidence and source evidence.

### Module 7 — Job Search Profile Management (critical)
Not permanently limited to Senior Java Developer. Multiple profiles, each with: target role, base resume, related
titles, primary technology family, mandatory technologies, supporting technologies, excluded technologies/role
families, preferred seniority, preferred locations, work arrangement prefs, contract prefs, work authorization
eligibility settings, relevance thresholds, ATS thresholds, associated resume categories, enabled flag.
Create a profile by uploading a base resume; the selected AI provider suggests titles, similar titles, primary and
supporting technologies, exclusions, skills/domains; user must approve. Multiple active profiles; prevent duplicate
applications across profiles.

### Module 8 — Dynamic Job Relevance Engine
Before ATS scoring, decide relevance to an active profile.
Default profile: Senior Java Developer. Relevant: Senior Java Developer, Java Backend Developer, Senior Backend Java
Developer, Java Spring Boot Developer, AWS Java Developer, Java Full Stack Developer, Java Developer with React,
Java Developer with Angular, Java Microservices Developer, Spring Boot Engineer, Backend Developer primarily using
Java, Senior Software Engineer primarily using Java.
Irrelevant: JavaScript Developer, React-only, Angular-only, .NET, C#, Python-only, Node.js-only backend, UI/UX
Designer, Network Engineer.
Never confuse Java with JavaScript. A frontend-only role is not relevant just because React/Angular is on a Java Full
Stack resume. Distinguish primary from incidental/integration technologies.
Score: core technology alignment 40, functional responsibilities 25, title/role family 20, seniority 10,
supporting tech/domain 5. Thresholds (configurable): <50 skip; 50–69 relevance review; 70–100 proceed.
Core Technology Gate rejects explicitly incompatible primary technology regardless of keyword overlap. Missing
primary technology → uncertainty, not automatic rejection. Record reasons for accept/reject.

### Module 9 — Eligibility and Duplicate Detection
Mandatory constraints separate from relevance/ATS: local only, no relocation, W2 only, C2C not accepted, work
authorization, mandatory industry experience, minimum years, certifications, geographic restrictions — configurable
per profile. Don't assume eligibility when critical info unknown.
Duplicates across Gmail messages, forwarded requirements, multiple recruiters for the same opening, multiple
profiles, previously submitted applications. Use normalized title, client, location, vendor, job reference, JD
similarity — not title alone. Different recruiters for the same opening → flag; user policy decides.

### Module 10 — Resume Library
Upload DOCX/PDF. Categorization, preview, parsed text, technology extraction, skills index, version history,
comparison, original preservation, editable metadata, download, archive/delete, assign to profiles. Categories e.g.
Java Backend, Java Full Stack, Java AWS, Java Azure, Java Healthcare/EDI, Java React, Java Angular, Data Engineering,
others. Never overwrite the original when tailoring.

### Module 11 — Verified Master Professional Profile
Structured, separate from resumes: employers, titles, dates, projects, responsibilities, technologies, domains,
certifications, education, achievements, confirmed metrics, experience duration. Edit/approve UI. Source of truth for
tailoring: may emphasize/reorganize supported experience, never fabricate experience, technologies, certifications,
employers, dates or statistics. Support evidence/references on approved facts.

### Module 12 — ATS Scoring and Resume Matching
For each relevant JD compare all active resumes of the profile. Deterministic, explainable score:
mandatory skills 35, preferred skills 15, responsibilities/domain 20, experience/seniority 15, hands-on evidence 10,
ATS readability 5 = 100. Estimated rubric, not an employer ATS guarantee.
Case-insensitive matching, configurable synonyms, taxonomy, keyword frequencies, mandatory vs optional, evidence in
employment/project bullets, experience duration, missing mandatory skills, explanations, JD-to-resume evidence
mapping, optional AI semantic review, separate recruiter-fit score, versioned scoring rules. Ranked display.
Default: best ≥91 → select; none above 90 → tailoring task. User overrides. No score inflation.

### Module 13 — AI Provider Gateway
Providers: Anthropic Claude (API adapter; browser handoff without key); OpenAI (API adapter; ChatGPT browser handoff
without key); Local Ollama (configurable model, availability checks, hardware warnings). Per-task provider selection
for: email classification, JD extraction, role profile generation, semantic relevance, resume scoring assistance,
resume tailoring, email drafting, resume validation, feedback analysis.
Capability-aware adapters exposing supported operations and unattended capability. No scraping of consumer AI sites,
cookie extraction, CAPTCHA bypass, unsupported login automation. Non-unattended → human-assisted handoff queue.
Credentials, connection testing, per-task selection, fallback, rate limiting, timeouts, retries, usage and cost
tracking, prompt versioning, model version tracking, structured outputs, schema validation, failure explanations.
AI failures never silently bypass validation. App remains useful without paid AI.

### Module 14 — Browser-Based AI Handoff
1 prepare JD; 2 select best current resume; 3 prepare relevant verified master-profile info; 4 generate tailoring
prompt; 5 export input files; 6 copy-prompt button; 7 open the AI website; 8 user submits files+prompt; 9 import
generated content or DOCX; 10 validate; 11 recalculate ATS; 12 return to workflow. Guided workflow with progress and
handoff status; never pretend it is automatic; replaceable later by programmatic providers without downstream impact.

### Module 15 — Resume Tailoring and Validation
1 receive JD + resume; 2 find missing/weak requirements; 3 check master profile for evidence; 4 generate prompt;
5 send to permitted provider or handoff; 6 receive revised content; 7 generate/import editable DOCX; 8 compare with
original; 9 detect unsupported additions; 10 verify chronology; 11 check formatting/readability; 12 rescore; 13 store
as new version; 14 accept/reject/revise. Evidence required for significant new claims; revision history and
comparison screen; never fabricate to hit a threshold; if no defensible resume meets threshold → manual decision.

### Module 16 — Email Draft Generation
Templates: new recruiter application, response to forwarded requirement, follow-up, with job reference number,
custom. Include correct recruiter email, subject, short intro, relevant technologies, concise experience summary,
selected resume attachment, configurable signature. Style: concise, human, ≤2–3 short paragraphs unless configured,
no keyword stuffing, only relevant verified skills, no long tech lists, no unsupported claims, no unrelated employer
details, no sensitive documents unless separately authorized, fully editable. Thread replies preserve thread/reply
headers; new messages get a professional subject even if the original had none.

### Module 17 — Approval and Application Sending
Modes: 1 Manual Approval; 2 Conditional Automation; 3 Rules-Based Automation (approved job families/vendors);
4 Full Autonomous (when all integrations and safety conditions available). First release starts in Manual; auto-send
disabled by default and requires explicit configuration.
Future automation settings: min relevance, min ATS, min recruiter-fit, validated classification confidence, approved
vendor/source, mandatory eligibility checks, max per day, max per vendor, working hours, whether tailored resumes need
approval, whether new vendors need review, only previously verified resumes, emergency pause/kill switch.
Enforce authorization on the backend; frontend can't bypass. State machine DRAFT → READY_FOR_REVIEW → APPROVED →
SENDING → SENT plus FAILED, CANCELLED, UNCERTAIN_SEND. Idempotent sending; on Gmail timeout don't blindly retry —
reconcile first. Record final Gmail message ID, recipient, attachment version, timestamp, status.

### Module 18 — Vendor Reply and Interview Tracking
Never trigger new applications. Link to existing applications; identify interview invitations, requests for info,
acknowledgments; update status; actionable notifications; conversation history; manual corrections. No automatic
replies to interviews or sending sensitive info unless a separately authorized workflow exists.

### Module 19 — Feedback and Learning Engine
Capture: incorrect classification, incorrect vendor details, relevant marked irrelevant and vice-versa, incorrect
resume selection, ATS disagreements, unsupported tailored claims, email draft corrections, approval/rejection
decisions. Store original decision, evidence, correction, final decision, AI provider/model, prompt version, rule
version, timestamp. Use to recommend rule adjustments, improve prompts, build reviewed example libraries, test new
models on history, improve ranking/classification. Acceptance ≠ every AI decision correct. Never auto-modify
high-impact rules or promote to autonomous without explicit approval. Evaluation dashboard: classification accuracy,
false positives/negatives, recipient errors, other metrics.

### Module 20 — Application Tracking and Analytics
Dashboard: emails scanned, requirements detected, relevant, irrelevant, needing review, duplicates, resume match
distribution, tailoring tasks, pending approvals, submitted, failed, vendor responses, interviews, history. Filter by
date, vendor, title, target role, location, resume, ATS score, status, AI provider. CSV export, basic reports.

### Module 21 — Settings and Administrative Controls
Gmail accounts, source rules, profiles, technology synonyms, relevance thresholds, ATS weights, recruiter-fit rules,
AI providers, prompt templates, resume templates, email templates, automation policies, daily limits, retry settings,
polling interval, notifications, backup/restore, logging and privacy. No source changes for normal preferences.

## 5. Required end-to-end workflow
1 receive/sync Gmail message → 2 sender/domain rules → 3 parse body, attachments, thread context → 4 classify →
5 ignore unrelated → 6 track replies/interviews separately → 7 extract one or more requirements → 8 extract
vendor/recruiter contact → 9 match against active profiles → 10 relevance → 11 skip irrelevant → 12 review borderline →
13 eligibility → 14 duplicates → 15 compare eligible resumes → 16 ATS + recruiter-fit → 17 select best if >90 →
18 else tailoring → 19 AI provider or handoff → 20 validate against verified experience → 21 rescore → 22 select/approve
final resume → 23 generate email → 24 attach approved resume version → 25 validate destination address → 26 check policy
and approval state → 27 queue for review or auto-send if authorized → 28 send via Gmail → 29 confirm result → 30 record
application → 31 track vendor responses → 32 capture feedback.
Every stage: persistent status, retries where appropriate, clear errors. Interrupted workflows resumable.

## 6. Required frontend screens
1 Login and First-Time Setup; 2 Main Dashboard; 3 Gmail Accounts and Connection Status; 4 Email Inbox /
Classification Review; 5 Email Source and Vendor Rules; 6 Job Search Profiles; 7 Extracted Job Details; 8 Resume
Library; 9 Master Professional Profile; 10 Job Relevance and ATS Comparison; 11 AI Provider Settings; 12 AI Tailoring
Workspace; 13 Application Approval Queue; 14 Email Composer and Resume Preview; 15 Submitted Applications; 16 Vendor
Replies and Interview Tracking; 17 Feedback and Decision History; 18 Automation Policy Settings; 19 Reports and
Analytics; 20 Application Settings, Backups, and Diagnostics.
Left sidebar; status indicators; searchable/sortable/paginated tables; filters; editable forms; side-by-side JD/resume
comparison; resume download/preview; progress for background tasks; actionable errors; notifications; confirmation
for sensitive actions; light/dark mode if practical; consistent spacing/typography; accessibility and keyboard nav.
Per-job decision history view: why considered a job, why relevant, why this resume, why score changed after
tailoring, why approved/skipped/sent. No unexplained AI scores.

## 7. Database design
Normalized PostgreSQL. Minimum entities: User, Workspace, GmailAccount, GmailSyncCheckpoint, IncomingEmail,
EmailAttachment, EmailSourceRule, EmailClassification, Vendor, RecruiterContact, JobRequirement, JobSearchProfile,
ProfileTechnologyRule, JobRelevanceResult, EligibilityDecision, JobDuplicateGroup, Resume, ResumeVersion,
MasterProfile, VerifiedExperienceFact, ResumeMatchResult, ATSScoreBreakdown, AIProviderConfiguration, AIExecution,
PromptTemplate, TailoringTask, EmailTemplate, ApplicationDraft, ApplicationApproval, JobApplication,
EmailSendAttempt, VendorConversationEvent, UserFeedback, AutomationPolicy, AutomationDecision, AuditEvent,
BackgroundJob. UUIDs where suitable; FKs; indexes; uniqueness; created/updated timestamps; ownership IDs;
validation; migrations; version tracking; optimistic locking where needed. Future multi-user. Reliable uniqueness
for mailbox IDs, Gmail message IDs, application IDs, document version IDs. Store original content and derived data
separately.

## 8. Backend package structure
auth, users, gmail, email, extraction, classification, vendors, jobs, profiles, relevance, eligibility, duplicates,
resumes, masterprofile, ats, ai, tailoring, documents, templates, applications, approvals, automation, feedback,
analytics, audit, scheduling, configuration, common. Within modules separate controllers, services, interfaces,
repositories, entities, DTOs, mappers, validation, exceptions, tests. DTOs not entities in APIs. Consistent error
responses, global exception handling, OpenAPI docs.

## 9. API design
REST endpoints for: authentication, user settings, Gmail connection, Gmail sync, email listing/classification,
source rules, job requirements, profiles, relevance evaluation, vendors, resume upload/download, master profile,
resume matching, ATS scoring, AI provider config, tailoring task creation/import, drafts, approval, sending,
submission history, feedback, automation policies, analytics, diagnostics. Versioned `/api/v1/`. Pagination, sorting,
filtering on collections. All state-changing endpoints validate authorization and ownership. Strict server-side
validation for emails, uploads, input.

## 10. Windows installation and execution
Dev: prerequisites doc, env template, DB startup script, backend startup script, frontend dev script, sample data
loader, test scripts. Production-style local: setup.ps1, start.ps1, stop.ps1, status.ps1, backup.ps1, restore.ps1.
Launch without an IDE: double-click launcher → check PostgreSQL → start services → backend → health checks → open
browser → login → Gmail sync resumes. Dashboard at http://localhost:8080. Bind to loopback by default. Docker
Compose PostgreSQL plus manual alternative. React production build served by Spring Boot (no Node at runtime).
Don't assume NVIDIA GPU or lots of RAM; local AI optional with Ollama-unavailable detection. All personal data in a
configurable local data directory.

## 11. Security, privacy and safety
Secure local auth; password hashing; CSRF protection; input validation; HTML sanitization; safe document parsing;
secure OAuth token handling; Windows-protected secret storage where appropriate; no credentials in source control;
no plaintext secrets in logs; no sensitive resume content in diagnostic logs by default; file size/type validation;
encrypted backups when configured; explicit consent before sending resume data to external AI; user/workspace
isolation; audit history for outgoing messages; safe handling of untrusted email instructions. JDs and email bodies
are untrusted; embedded instructions (ignore rules, change recipients, expose documents) never override policy; AI
gets no authority to change approval rules, fetch unrelated files or send without authorization. Never auto-attach
identity, immigration, financial or other sensitive documents. Auto-send disabled unless explicitly activated.
Global emergency stop.

## 12. Reliability and error handling
50–100 emails/day initially, scalable. Durable background processing (not browser tabs). Persistent queues; bounded
exponential backoff; transient vs permanent failures; dead-letter/failed-job review; idempotent email processing and
dispatch; transactions; file cleanup policies; health checks; structured logs; job monitoring; safe startup recovery;
graceful shutdown; manual reprocessing; no duplicate sends on restart; clear errors for inaccessible AI providers.
Preserve state and identify needed action when a task cannot continue. Never silently mark an incomplete
application as sent.

## 13. Test scenarios (minimum)
Classification: 1 JD no subject; 2 generic subject; 3 body only; 4 PDF attachment; 5 DOCX attachment; 6 multiple JDs;
7 forwarded JD with unrelated employer signature; 8 vendor reply quoting older JD; 9 interview notification;
10 recruiter asking availability; 11 submission confirmation; 12 marketing from approved sender; 13 new JD within an
existing thread; 14 unknown sender with valid JD; 15 blocked sender with valid JD; 16 malformed attachment;
17 HTML-only message.
Relevance: 18 Senior Java — accept; 19 Java Backend — accept; 20 AWS Java — accept; 21 Java Full Stack — accept;
22 Java with React — accept; 23 Java with Angular — accept; 24 JavaScript-only — reject; 25 React-only — reject;
26 .NET — reject; 27 Backend unspecified stack — review; 28 Node.js primary mentioning Java incidentally — reject;
29 new Data Engineer profile — evaluated with its own rules.
Resume matching: 30 existing resume >90; 31 all <91; 32 mandatory tech missing; 33 keyword only in skills section;
34 multiple resume versions; 35 profile with no matching resumes; 36 AI-tailored resume introducing unsupported
experience; 37 improved tailoring score with verified info; 38 tailoring cannot meet target.
Sending: 39 manual approval success; 40 user rejects draft; 41 user edits recipient/message; 42 missing attachment;
43 invalid recipient; 44 duplicate submission; 45 Gmail access revoked; 46 Gmail send timeout; 47 restart during
processing; 48 auto-send disabled; 49 auto-send threshold not met; 50 daily limit reached; 51 existing thread needs
reply headers; 52 uncertain send must be reconciled.
Security/multi-user: 53 malicious instructions in JD; 54 unauthorized API access; 55 cross-user isolation;
56 invalid/oversized upload; 57 secrets in logs; 58 AI provider unavailable; 59 corrupted backup; 60 duplicate Gmail
history events.
Anonymized dataset of 100+ recruiter messages for classification evaluation; measure precision and recall. Regression
tests whenever a real-world bug is fixed.

## 14. Documentation requirements
README.md, ARCHITECTURE.md, REQUIREMENTS.md, DATABASE_DESIGN.md, API_DOCUMENTATION.md, WINDOWS_SETUP.md,
GMAIL_OAUTH_SETUP.md, AI_PROVIDER_SETUP.md, RESUME_SCORING.md, AUTOMATION_SAFETY.md, SECURITY.md, TROUBLESHOOTING.md,
TESTING.md, DEPLOYMENT.md, FUTURE_ENHANCEMENTS.md, CHANGELOG.md, PROGRESS.md, CLAUDE.md.
CLAUDE.md must include: purpose, architectural decisions, module responsibilities, coding conventions, build and test
commands, environment requirements, business rules, security restrictions, how to add AI providers, job profiles,
change scoring rules, add classification categories, add/modify modules, record issues and regression tests.

## 15. Implementation strategy (phases)
1 Foundation: repo structure, Spring Boot, React, PostgreSQL, Flyway, authentication, main dashboard, Windows dev scripts.
2 Gmail: OAuth, connect/disconnect, inbox sync, message storage, attachment parsing, sender filtering.
3 Classification & Extraction: classification, signature handling, JD extraction, vendor extraction, review UI, edge-case tests.
4 Job Search Intelligence: profiles, technology taxonomy, similar-role matching, relevance scoring, eligibility, duplicates.
5 Resume Intelligence: library, master profile, parser, ATS scoring, ranking, evidence-based explanations.
6 AI Integration: gateway, provider settings, Ollama, Claude API, OpenAI API, Claude/ChatGPT handoff, prompt management.
7 Resume Tailoring: workflow, DOCX generation, version comparison, validation, rescoring, review.
8 Application Management: email templates, composer, attachments, manual approval, Gmail draft/send, submission tracking, vendor reply tracking.
9 Feedback & Automation: feedback, decision evaluation, classification improvements, automation policies, conditional sending, safety limits, kill switch.
10 Final Hardening: full integration tests, E2E workflow tests, Windows launcher, backup/restore, security review, docs, packaging.
After every phase: compile, run tests, resolve errors, verify, update PROGRESS.md and docs, record limitations.
Never claim functionality works unless implemented and tested. Where credentials are needed, implement and test with
safe fixtures and document exact remaining setup.

## 16. Future extensibility and change management
Expect new Gmail formats, forwarding patterns, vendor layouts, JD styles, skill terminology, job categories,
misclassifications, scoring issues, AI failures, document formatting problems, duplicate scenarios. Use
configuration rules, provider adapters, versioned prompts, versioned scoring rules, migrations, feature flags,
domain interfaces, regression tests, documented extension points.
Bug process: understand → reproduce from data/fixtures → root cause → affected module → smallest maintainable fix →
regression test → run relevant tests → verify unrelated functionality → update docs/changelog → explain change.
No full rewrites for small fixes. No hardcoded sender/technology/preference/correction unless it belongs in
configurable rules or verified defaults. Protect user data during upgrades (Flyway + backups).

## 17. Acceptance criteria
1 launch on Windows; 2 log in securely; 3 connect Gmail via OAuth; 4 configure multiple approved senders/domains;
5 scans Gmail; 6 handles missing subjects; 7 distinguishes new requirements from replies/interviews; 8 extracts JDs
and vendor details; 9 filters irrelevant roles per profile; 10 distinguishes Java from JavaScript; 11 multiple
resumes; 12 verified master profile; 13 explainable ATS scores; 14 selects highest-scoring eligible resume;
15 tailoring task when none passes; 16 uses configured AI providers or browser handoffs; 17 generates and validates
revised DOCX; 18 prepares application email; 19 attaches correct resume; 20 manual approval by default; 21 sends
approved messages via Gmail; 22 prevents duplicate submissions; 23 tracks submissions and replies; 24 stores feedback
and decisions; 25 recovers from restarts/common failures; 26 major functions covered by automated tests; 27 usable
dashboard, not demo screens; 28 operates without paid AI; 29 architecture supports future automated sending via
permitted unattended AI; 30 modules modifiable without rebuilding everything.

## 18. Important development instructions
Real working project; not just architecture docs; no pseudocode; no fake integration success responses; no empty core
methods; mock data only for labelled dev/test scenarios; runs before Gmail credentials exist via demo mode; separate
production and demo behavior; no real emails in automated tests; default to dry-run until outbound sending is
explicitly configured; correctness over complexity; maintainable code; structured error handling; clear setup docs;
record unfinished tasks transparently; don't invent credentials or claim integrations configured; no destructive
actions or real sends without authorization; don't weaken security to make tests pass. When external dependencies
aren't configured, finish every feasible part and document the missing setup. If execution limits interrupt work,
preserve state in PROGRESS.md so the next session can continue without re-planning.

## 19. What to do first
Inspect environment; concise architecture with module boundaries and data flow; verify dependency versions; full
repo structure; CLAUDE.md and PROGRESS.md; schema and initial migrations; Spring Boot app; React app; real dashboard
and login; Windows setup/launch scripts; make Phase 1 runnable; run tests, fix, verify startup; continue phases in
order. Ask only when credentials, authorization, hardware details or an important non-inferable preference is needed.
At the end of each phase report: features implemented, files changed, tests and results, how to run, what's missing,
next phase.

## Session constraint (owner instruction)
Work in phases; each phase/cloud session should be ≤ ~1 hour. Stop after a phase completes; the next session resumes
from PROGRESS.md when the owner says to start the next phase.
