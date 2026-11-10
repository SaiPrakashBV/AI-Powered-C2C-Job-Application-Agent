import { expect, test } from '@playwright/test';

const user = { username: 'e2e-owner', displayName: 'E2E Owner', password: 'E2e-Strong-Pass-1' };

test.describe.serial('Phase 1 foundation', () => {
  test('first-time setup, dashboard, lock, login and emergency stop', async ({ page }) => {
    await page.goto('/');
    await expect(page.getByText(/first-time setup/i)).toBeVisible();
    await page.getByLabel('Username').fill(user.username);
    await page.getByLabel('Display name').fill(user.displayName);
    await page.getByLabel('Password', { exact: true }).fill(user.password);
    await page.getByLabel('Confirm password').fill(user.password);
    await page.getByRole('button', { name: 'Create account' }).click();

    await expect(page.getByRole('heading', { name: 'Dashboard' })).toBeVisible();
    await expect(page.getByText('Manual approval', { exact: true })).toBeVisible();
    await expect(page.getByText('First-time setup completed; owner account created')).toBeVisible();

    // Deep link + refresh is served by Spring Boot's SPA forwarding.
    await page.goto('/settings');
    await expect(page.getByRole('heading', { name: 'Settings & Diagnostics' })).toBeVisible();
    await page.getByRole('button', { name: 'Engage emergency stop' }).click();
    await page.getByRole('button', { name: 'Confirm' }).click();
    await expect(page.getByText('Emergency stop engaged', { exact: true })).toBeVisible();
    await page.getByRole('button', { name: 'Release emergency stop' }).click();
    await page.getByRole('button', { name: 'Confirm' }).click();
    await expect(page.getByText('Sending: manual approval required')).toBeVisible();

    await page.getByRole('button', { name: 'Run self-test' }).click();
    await expect(page.getByRole('cell', { name: 'DIAGNOSTICS_SELF_TEST' })).toBeVisible();
    await expect(page.getByText('SUCCEEDED').first()).toBeVisible({ timeout: 20_000 });

    await page.getByRole('button', { name: 'Lock' }).click();
    await expect(page.getByRole('heading', { name: 'Sign in' })).toBeVisible();

    await page.getByLabel('Username').fill(user.username);
    await page.getByLabel('Password').fill('wrong-password-9');
    await page.getByRole('button', { name: 'Sign in' }).click();
    await expect(page.getByText('Invalid username or password')).toBeVisible();

    await page.getByLabel('Password').fill(user.password);
    await page.getByRole('button', { name: 'Sign in' }).click();
    // Unlocking returns the user to the page they locked from.
    await expect(page.getByRole('heading', { name: 'Settings & Diagnostics' })).toBeVisible();
    await page.getByRole('link', { name: 'Dashboard' }).click();
    await expect(page.getByRole('heading', { name: 'Dashboard' })).toBeVisible();
  });

  test('API rejects unauthenticated access', async ({ request }) => {
    const res = await request.get('/api/v1/dashboard/summary');
    expect(res.status()).toBe(401);
  });
});
