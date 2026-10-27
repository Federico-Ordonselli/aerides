import { defineConfig, devices } from '@playwright/test';

/**
 * The tests run against a stack that is already up (`docker compose up`), not against a dev server:
 * the point is to test nginx, the backend, PostgreSQL, WebSocket and the emails together.
 * Override the addresses with E2E_BASE_URL and E2E_MAILPIT_URL.
 */
export default defineConfig({
  testDir: './tests',
  // Each test registers its own users, so tests never depend on each other or on existing data
  fullyParallel: true,
  forbidOnly: !!process.env['CI'],
  // No retries: a flaky end-to-end test must be fixed, not hidden
  retries: 0,
  reporter: process.env['CI'] ? [['github'], ['html', { open: 'never' }]] : 'list',
  use: {
    baseURL: process.env['E2E_BASE_URL'] ?? 'http://localhost:4200',
    // English UI and emails: the app picks the browser language
    locale: 'en-US',
    timezoneId: 'Europe/Rome',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
});
