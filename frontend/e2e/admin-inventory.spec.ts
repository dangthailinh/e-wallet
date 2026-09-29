import { expect, test } from '@playwright/test';

const productId = 'ad3a2870-f25a-4af4-bf68-9d6caf211111';
const itemId = 'bd3a2870-f25a-4af4-bf68-9d6caf211111';
const product = {
  id: productId,
  category: { id: 'fa560230-a6ae-4dab-9dce-63a44b811111', name: 'Gói API', slug: 'api-packages' },
  name: 'API 10M Token Codex',
  slug: 'api-10m-token-codex',
  priceVnd: 50_000,
  imageUrl: null,
  availableCount: 1,
};
const inventory = {
  productId,
  counts: { AVAILABLE: 1, RESERVED: 0, SOLD: 0, DISABLED: 0 },
  items: [{
    id: itemId,
    productId,
    status: 'AVAILABLE',
    checksumSuffix: 'a1b2c3',
    keyVersion: 1,
    createdAt: '2026-09-18T01:00:00Z',
    updatedAt: '2026-09-18T01:00:00Z',
  }],
};

test('inventory import clears plaintext and renders masked rows', async ({ page }) => {
  let submittedBody = '';
  await mockSession(page, ['ROLE_ADMIN']);
  await page.route('**/api/v1/auth/csrf', (route) => route.fulfill({
    status: 204,
    headers: { 'set-cookie': 'XSRF-TOKEN=e2e-csrf; Path=/; SameSite=Lax' },
  }));
  await page.route('**/api/v1/products?*', (route) => route.fulfill({
    status: 200,
    contentType: 'application/json',
    body: JSON.stringify({
      items: [product],
      page: { number: 0, size: 100, totalItems: 1, totalPages: 1, hasNext: false, hasPrevious: false },
    }),
  }));
  await page.route(`**/api/v1/admin/products/${productId}/inventory-items`, async (route) => {
    if (route.request().method() === 'POST') {
      submittedBody = route.request().postData() ?? '';
      expect(route.request().headers()['x-xsrf-token']).toBe('e2e-csrf');
      await route.fulfill({
        status: 201,
        contentType: 'application/json',
        body: JSON.stringify({
          requested: 1,
          imported: 1,
          results: [{ row: 1, status: 'IMPORTED', inventoryItemId: itemId, code: null, detail: null }],
        }),
      });
      return;
    }
    await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(inventory) });
  });

  await page.goto('/admin/inventory');
  await expect(page.getByText('••••a1b2c3')).toBeVisible();
  const input = page.getByRole('textbox', { name: 'Mỗi dòng là một tài khoản, mã hoặc API key' });
  await input.fill('e2e-secret-that-must-disappear');
  await page.getByRole('button', { name: 'Mã hóa và nhập kho' }).click();

  await expect(input).toHaveValue('');
  await expect(page.getByText('Dòng 1: Đã nhập')).toBeVisible();
  await expect(page.getByText('e2e-secret-that-must-disappear')).toHaveCount(0);
  expect(submittedBody).toContain('e2e-secret-that-must-disappear');
});

test('inventory import route denies a customer', async ({ page }) => {
  await mockSession(page, ['ROLE_USER']);
  await page.route('**/api/v1/products?*', (route) => route.fulfill({
    status: 200,
    contentType: 'application/json',
    body: JSON.stringify({
      items: [product],
      page: { number: 0, size: 100, totalItems: 1, totalPages: 1, hasNext: false, hasPrevious: false },
    }),
  }));
  await page.route(`**/api/v1/admin/products/${productId}/inventory-items`, (route) => route.fulfill({
    status: 403,
    contentType: 'application/problem+json',
    body: JSON.stringify({ code: 'ACCESS_DENIED', detail: 'Forbidden' }),
  }));

  await page.goto('/admin/inventory');
  await expect(page.getByText('Tài khoản hiện tại không có quyền truy cập kho sản phẩm.')).toBeVisible();
  await expect(page.getByRole('textbox', { name: 'Mỗi dòng là một tài khoản, mã hoặc API key' })).toHaveCount(0);
});

async function mockSession(page: import('@playwright/test').Page, roles: string[]) {
  await page.route('**/api/v1/auth/me', (route) => route.fulfill({
    status: 200,
    contentType: 'application/json',
    body: JSON.stringify({ id: 7, username: 'admin', firstName: 'Test', lastName: 'Admin', roles }),
  }));
}
