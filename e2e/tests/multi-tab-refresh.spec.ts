import { expect, Page, test } from '@playwright/test';

import { registerUser } from './support';

const LOCK_NAME = 'aerides-refresh';

/** The refresh lock as the browser sees it, for every tab of the origin. */
async function refreshLock(page: Page): Promise<{ held: number; pending: number }> {
  return page.evaluate(async (name) => {
    const state = await navigator.locks.query();
    return {
      held: (state.held ?? []).filter((lock) => lock.name === name).length,
      pending: (state.pending ?? []).filter((lock) => lock.name === name).length,
    };
  }, LOCK_NAME);
}

/**
 * Two tabs share the refresh cookie. When both restore the session at once (the browser reopening them),
 * the second must wait for the first: sending the same refresh token twice looks like a replayed token
 * and logs the user out everywhere.
 *
 * Each run sends about four refreshes from 127.0.0.1: repeating it many times within a minute
 * hits the refresh rate limit (RATE_LIMIT_REFRESH_PER_IP, 20 a minute) and the tabs get 429.
 */
test('two tabs refreshing at once take turns and both stay logged in', async ({ browser }) => {
  const user = await registerUser(browser, 'Tabs');
  const first = user.page;
  const second = await first.context().newPage();
  await second.goto('/');
  await expect(second.getByRole('heading', { name: 'Hi, Tabs!' })).toBeVisible();

  // Hold the first tab's refresh, so it keeps the lock as long as the test needs
  let release!: () => void;
  const gate = new Promise<void>((resolve) => (release = resolve));
  await first.route('**/api/auth/refresh', async (route) => {
    await gate;
    await route.continue();
  });
  const secondTabRefreshes: string[] = [];
  second.on('request', (request) => {
    if (request.url().endsWith('/api/auth/refresh')) {
      secondTabRefreshes.push(request.url());
    }
  });

  await first.reload({ waitUntil: 'commit' });
  await expect.poll(() => refreshLock(second)).toEqual({ held: 1, pending: 0 });
  await second.reload({ waitUntil: 'commit' });
  await expect.poll(() => refreshLock(first)).toEqual({ held: 1, pending: 1 });

  // The second tab is queued behind the first: it hasn't sent the shared refresh token
  expect(secondTabRefreshes).toEqual([]);

  release();
  await expect(first.getByRole('heading', { name: 'Hi, Tabs!' })).toBeVisible();
  await expect(second.getByRole('heading', { name: 'Hi, Tabs!' })).toBeVisible();

  // No session was revoked: a new tab still gets in with the cookie
  const third = await first.context().newPage();
  await third.goto('/');
  await expect(third.getByRole('heading', { name: 'Hi, Tabs!' })).toBeVisible();
});
