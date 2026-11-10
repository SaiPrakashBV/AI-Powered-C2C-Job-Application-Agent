import { defineConfig } from '@playwright/test';

// E2E tests run against a running application (default http://127.0.0.1:8080) backed by a DISPOSABLE database:
// the first test performs first-time setup. Start it with scripts\start.ps1 against a fresh test DB.
export default defineConfig({
  testDir: './e2e',
  timeout: 30_000,
  retries: 0,
  workers: 1,
  use: {
    baseURL: process.env.E2E_BASE_URL ?? 'http://127.0.0.1:8080',
    trace: 'off',
    launchOptions: process.env.PW_CHROMIUM_PATH ? { executablePath: process.env.PW_CHROMIUM_PATH } : {},
  },
});
