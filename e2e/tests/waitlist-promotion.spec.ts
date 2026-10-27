import { expect, test } from '@playwright/test';

import { emailSubjectsTo, registerUser, uniqueId } from './support';

/**
 * The main flow of the app, with two real users in two browsers:
 * register → create a group → create and publish an event → a second user joins and signs up →
 * the slot is full, so they are waitlisted → the first user withdraws → the second one is promoted,
 * sees it live without reloading (WebSocket) and gets an email.
 */
test('a withdrawal promotes the waitlisted user, live and by email', async ({ browser, request }) => {
  const owner = await registerUser(browser, 'Owner');
  const member = await registerUser(browser, 'Member');
  const groupName = `E2E guild ${uniqueId()}`;

  await test.step('the owner creates a group and reads its invite code', async () => {
    await owner.page.goto('/groups');
    await owner.page.getByTestId('create-group').click();
    await owner.page.getByTestId('group-name-input').fill(groupName);
    await owner.page.getByRole('button', { name: 'Create', exact: true }).click();
    await expect(owner.page.getByTestId('group-name')).toHaveText(groupName);
  });
  const groupUrl = owner.page.url();
  await owner.page.getByTestId('settings-link').click();
  const inviteCode = (await owner.page.getByTestId('invite-code').textContent())?.trim() ?? '';
  expect(inviteCode).not.toBe('');

  await test.step('the owner creates an event with one seat, publishes it and takes the seat', async () => {
    await owner.page.goto(`${groupUrl}/events/new`);
    await owner.page.getByTestId('title').fill('Raid night');
    // The date defaults to tomorrow at 21:00
    await owner.page.getByTestId('role-name').fill('Tank');
    await owner.page.getByTestId('capacity').fill('1');
    await owner.page.getByTestId('save-event').click();

    await expect(owner.page.getByTestId('event-title')).toHaveText('Raid night');
    await owner.page.getByTestId('publish-event').click();
    await owner.page.getByTestId('sign-up').click();
    await expect(owner.page.getByTestId('my-signup')).toContainText("You're in as Tank");
  });
  const eventUrl = owner.page.url();

  await test.step('the member joins with the code and ends up on the waitlist', async () => {
    await member.page.goto('/groups');
    await member.page.getByTestId('join-group').click();
    await member.page.getByLabel('Invite code').fill(inviteCode);
    await member.page.getByRole('button', { name: 'Join', exact: true }).click();
    await expect(member.page.getByTestId('group-name')).toHaveText(groupName);

    await member.page.goto(eventUrl);
    await member.page.getByRole('button', { name: 'Join the waitlist' }).click();
    await expect(member.page.getByTestId('my-signup')).toContainText("You're number 1 on the Tank waitlist");
  });

  await test.step('the owner withdraws', async () => {
    await owner.page.getByTestId('withdraw').click();
    await owner.page.getByRole('dialog').getByRole('button', { name: 'Withdraw' }).click();
    await expect(owner.page.getByTestId('sign-up')).toBeVisible();
  });

  await test.step('the member is promoted: the open page updates by itself, and an email arrives', async () => {
    // No reload: the change comes over the WebSocket
    await expect(member.page.getByTestId('my-signup')).toContainText("You're in as Tank");
    await expect(member.page.getByText('A seat opened up: you are now confirmed for “Raid night”')).toBeVisible();
    await expect
      .poll(() => emailSubjectsTo(request, member.email), { timeout: 15_000 })
      .toContain('You got a seat: Raid night');
  });
});
