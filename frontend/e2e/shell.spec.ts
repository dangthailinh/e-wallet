import AxeBuilder from '@axe-core/playwright';
import { expect, test } from '@playwright/test';

test('shell renders without console or accessibility errors', async ({ page }) => {
  const browserErrors: string[] = [];

  await page.route('**/api/v1/auth/me', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        id: 7,
        username: 'shell-buyer',
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
    if (message.type() === 'error') {
      browserErrors.push(message.text());
    }
  });
  page.on('pageerror', (error) => {
    browserErrors.push(error.message);
  });

  await page.goto('/');

  await expect(page.getByRole('heading', { level: 1, name: 'Chọn sản phẩm. Trả bằng ví. Nhận ngay.' })).toBeVisible();
  await expect(page.getByRole('searchbox', { name: 'Tìm sản phẩm số' })).toBeVisible();

  const accessibilityResults = await new AxeBuilder({ page })
    .withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'])
    .analyze();

  expect(accessibilityResults.violations).toEqual([]);
  expect(browserErrors).toEqual([]);
});
