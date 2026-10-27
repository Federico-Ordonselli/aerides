import { APIRequestContext, Browser, expect, Page } from '@playwright/test';

export const MAILPIT_URL = process.env['E2E_MAILPIT_URL'] ?? 'http://localhost:8025';

export interface TestUser {
  name: string;
  email: string;
  /** A page in the user's own browser context: separate cookies, separate session. */
  page: Page;
}

/** A unique suffix, so every run creates new users and groups and can be repeated on the same database. */
export function uniqueId(): string {
  return `${Date.now()}-${Math.floor(Math.random() * 1_000_000)}`;
}

/** Opens a new browser context and signs a new user up through the registration form. */
export async function registerUser(browser: Browser, name: string): Promise<TestUser> {
  const context = await browser.newContext();
  const page = await context.newPage();
  const email = `e2e-${name.toLowerCase()}-${uniqueId()}@example.com`;

  await page.goto('/register');
  await page.getByLabel('Display name').fill(name);
  await page.getByLabel('Email').fill(email);
  await page.getByLabel('Password', { exact: true }).fill('supersecret');
  await page.getByLabel('Confirm password').fill('supersecret');
  await page.getByRole('button', { name: 'Sign up' }).click();

  await expect(page.getByRole('heading', { name: `Hi, ${name}!` })).toBeVisible();
  return { name, email, page };
}

/** Subjects of the emails Mailpit received for one address. */
export async function emailSubjectsTo(request: APIRequestContext, email: string): Promise<string[]> {
  const response = await request.get(`${MAILPIT_URL}/api/v1/search`, { params: { query: `to:"${email}"` } });
  expect(response.ok()).toBeTruthy();
  const body = (await response.json()) as { messages: { Subject: string }[] };
  return body.messages.map((message) => message.Subject);
}
