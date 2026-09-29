import { expect, test } from '@playwright/test';

test('catalog search keeps filters while switching views', async ({ page }) => {
  const browserErrors: string[] = [];
  await page.route('**/api/v1/auth/me', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        id: 7,
        username: 'catalog-buyer',
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

  await page.goto('/search');
  await page.getByRole('searchbox', { name: 'Tìm trong danh mục' }).fill('Codex');
  await page.getByRole('combobox', { name: 'Danh mục' }).selectOption('api-packages');
  await page.getByRole('combobox', { name: 'Tồn kho' }).selectOption('in-stock');
  await page.getByRole('combobox', { name: 'Sắp xếp' }).selectOption('price-asc');
  await page.getByRole('button', { name: 'Áp dụng' }).click();

  await expect(page).toHaveURL(/\/search\?q=Codex&category=api-packages&availability=in-stock&sort=price-asc&view=grid/);
  await expect(page.getByRole('heading', { name: 'API 10M Token Codex' })).toBeVisible();

  const listToggle = page.getByRole('link', { name: 'Xem dạng danh sách' });
  await expect(listToggle).toHaveAttribute(
    'href',
    '/search?q=Codex&category=api-packages&availability=in-stock&sort=price-asc&view=list'
  );
  await listToggle.click();
  await expect(page.locator('.catalog-result-row')).toHaveCount(1);
  await expect(page.getByRole('combobox', { name: 'Danh mục' })).toHaveValue('api-packages');
  expect(browserErrors).toEqual([]);
});

test('catalog search remains usable on a mobile viewport', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await page.goto('/search');

  await expect(page.getByRole('searchbox', { name: 'Tìm trong danh mục' })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Áp dụng' })).toBeVisible();
  await expect(page.getByRole('heading', { name: 'API 10M Token Codex' })).toBeVisible();
  // The dev catalog contains the complete 18-product showcase; the viewport
  // smoke check only asserts that the responsive grid remains populated.
  await expect(page.locator('.catalog-card')).toHaveCount(18);
});
