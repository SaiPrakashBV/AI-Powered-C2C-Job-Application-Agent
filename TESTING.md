# Testing

| Layer | Tooling | Location | Command |
|---|---|---|---|
| Backend unit | JUnit 5, Mockito, AssertJ | `backend/src/test/java/**/*Test.java` | `.\mvnw.cmd -B test` |
| Backend integration | Spring Boot test, MockMvc, real PostgreSQL + Flyway | `**/*IT.java` | same |
| Frontend unit/component | Vitest, React Testing Library, jsdom | `frontend/src/**/*.test.tsx` | `npm test` |
| End-to-end | Playwright | `frontend/e2e` | `npx playwright test --trace off` |

## Database for integration tests
`IntegrationTestBase` uses **Testcontainers** (`postgres:16-alpine`, needs Docker) unless `C2C_TEST_DB_URL` is
set. Then it uses that database together with `C2C_TEST_DB_USER` / `C2C_TEST_DB_PASSWORD`. **Every table in that
database is truncated before each test**, so never point it at your real database.

```powershell
$env:C2C_TEST_DB_URL="jdbc:postgresql://localhost:5432/c2c_agent_test"; $env:C2C_TEST_DB_USER="c2c"; $env:C2C_TEST_DB_PASSWORD="..."
cd backend; .\mvnw.cmd -B test
```
The background job worker is disabled in tests (`app.jobs.worker-enabled=false`). Tests call `JobWorker.runOnce()`
for deterministic execution.

## E2E
1. Create an empty database, e.g. `c2c_agent_e2e`.
2. Start the packaged app against it (`C2C_DB_URL=...c2c_agent_e2e`, separate `C2C_DATA_DIR`).
3. `cd frontend; npx playwright test --trace off`. The first test performs first-time setup, so the DB must be empty.

## Rules
- No real Gmail, AI provider or outbound email in automated tests. Use fixture implementations or WireMock.
- Every fixed real-world bug gets a regression test named after the scenario (see CLAUDE.md).
- Spec §13 scenarios are tracked in REQUIREMENTS.md (X-8) as they are implemented.

## Current results (Phase 1)
Backend 21/21, frontend 5/5, E2E 2/2 (see PROGRESS.md for the environment).
