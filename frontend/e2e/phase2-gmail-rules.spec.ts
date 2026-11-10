import { expect, test } from '@playwright/test';

// Runs after foundation.spec.ts (same disposable DB): the owner account already exists.
const user = { username: 'e2e-owner', password: 'E2e-Strong-Pass-1' };

test('Phase 2: source rules CRUD, sender test, Gmail page without OAuth client, empty inbox', async ({ page }) => {
  await page.goto('/source-rules');
  await page.getByLabel('Username').fill(user.username);
  await page.getByLabel('Password').fill(user.password);
  await page.getByRole('button', { name: 'Sign in' }).click();
  await expect(page.getByRole('heading', { name: 'Email Source & Vendor Rules' })).toBeVisible();

  await page.getByLabel('Value').fill('recruiter@vendor-a.com');
  await page.getByRole('button', { name: 'Add rule' }).click();
  await expect(page.getByRole('cell', { name: 'recruiter@vendor-a.com', exact: true })).toBeVisible();

  await page.getByLabel('Rule type').selectOption('BLOCK_DOMAIN');
  await page.getByLabel('Value').fill('@promo-mail.com');
  await page.getByRole('button', { name: 'Add rule' }).click();
  await expect(page.getByRole('cell', { name: 'promo-mail.com', exact: true })).toBeVisible();

  await page.getByLabel('Sender address').fill('deals@promo-mail.com');
  await page.getByRole('button', { name: 'Evaluate' }).click();
  await expect(page.getByText('BLOCKED', { exact: true })).toBeVisible();

  await page.getByLabel('Sender address').fill('Recruiter <RECRUITER@vendor-a.com>');
  await page.getByRole('button', { name: 'Evaluate' }).click();
  await expect(page.getByText(/is approved \(content classification still required\)/)).toBeVisible();

  await page.getByRole('link', { name: 'Gmail Accounts' }).click();
  await expect(page.getByText('Google OAuth client not configured')).toBeVisible();
  await expect(page.getByRole('button', { name: 'Connect Gmail account' })).toBeDisabled();

  await page.getByRole('link', { name: /Inbox/ }).click();
  await expect(page.getByText('No emails yet. Connect a Gmail account and run a sync.')).toBeVisible();
});
