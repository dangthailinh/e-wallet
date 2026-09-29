import AxeBuilder from '@axe-core/playwright';
import { expect, test } from '@playwright/test';

test('product detail renders at desktop and mobile breakpoints', async ({ page }) => {
  const browserErrors: string[] = [];
  await page.route('**/api/v1/auth/me', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        id: 7,
        username: 'product-buyer',
        firstName: 'Test',
        lastName: 'Buyer',
        roles: ['ROLE_USER'],
      }),
    });
  });
  await page.route('**/api/v1/wallet', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ walletId: 7001, balanceVnd: 0, currency: 'VND' }),
    });
  });
  await page.route('**/api/v1/wallet/entries**', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ items: [], page: 0, size: 5, totalItems: 0, totalPages: 0 }),
    });
  });
  page.on('console', (message) => {
    if (message.type() === 'error') browserErrors.push(message.text());
  });
  page.on('pageerror', (error) => browserErrors.push(error.message));

  await page.goto('/product/codex-10m-1-day');
  await expect(page.getByRole('heading', { level: 1, name: 'API 10M Token Codex' })).toBeVisible();
  await expect(page.getByText('Còn 11 sản phẩm')).toBeVisible();
  // A logged-in customer can open the purchase flow even with an empty
  // wallet; the dialog explains that the balance is insufficient.
  await expect(page.getByRole('button', { name: 'Mua bằng số dư ví' })).toBeEnabled();
  expect(await page.locator('script[type="application/ld+json"]').textContent()).toContain('InStock');

  const accessibility = await new AxeBuilder({ page })
    .withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'])
    .analyze();
  expect(accessibility.violations.filter((violation) => ['serious', 'critical'].includes(violation.impact ?? '')))
    .toEqual([]);

  await page.setViewportSize({ width: 390, height: 844 });
  await page.reload();
  await expect(page.getByRole('heading', { level: 1, name: 'API 10M Token Codex' })).toBeVisible();
  await expect(page.getByRole('img', { name: 'Minh họa API 10M Token Codex' })).toBeVisible();
  expect(browserErrors).toEqual([]);
});

test('missing product uses the not-found page', async ({ page }) => {
  await page.goto('/product/missing-product');
  await expect(page.getByRole('heading', { level: 1, name: 'Liên kết này không còn tồn tại.' })).toBeVisible();
});
