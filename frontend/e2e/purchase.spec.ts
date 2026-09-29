import { expect, test } from '@playwright/test';

import { installPurchaseFixture } from './fixtures/purchase';

test('digital purchase survives duplicate submit and protects delivery data', async ({ page }) => {
  const fixture = await installPurchaseFixture(page);
  const browserErrors: string[] = [];
  const unexpectedHttpErrors: string[] = [];
  page.on('console', (message) => {
    if (message.type() === 'error' && !message.text().startsWith('Failed to load resource:')) {
      browserErrors.push(message.text());
    }
  });
  page.on('pageerror', (error) => browserErrors.push(error.message));
  page.on('response', (response) => {
    if (response.status() < 400) return;
    const pathname = new URL(response.url()).pathname;
    const expectedAuthFailure =
      response.status() === 401 && ['/api/v1/auth/me', '/api/v1/auth/refresh'].includes(pathname);
    const expectedOwnershipFailure = response.status() === 404 && pathname.startsWith('/api/v1/orders/');
    if (!expectedAuthFailure && !expectedOwnershipFailure) {
      unexpectedHttpErrors.push(`${response.status()} ${pathname}`);
    }
  });

  await page.goto('/login');
  await page.getByLabel('Tên đăng nhập', { exact: true }).fill(fixture.credentials.username);
  await page.getByLabel('Mật khẩu', { exact: true }).fill(fixture.credentials.password);
  await page.getByRole('button', { name: 'Đăng nhập', exact: true }).click();
  await expect(page).toHaveURL('/');

  await page.goto(`/product/${fixture.product.slug}`);
  await page.getByRole('button', { name: 'Mua bằng số dư ví' }).click();
  const dialog = page.getByRole('dialog', { name: 'Xác nhận thanh toán' });
  await expect(dialog).toContainText('125.000 ₫');
  await expect(dialog).toContainText('75.000 ₫');

  const confirm = page.getByRole('button', { name: 'Xác nhận mua' });
  await confirm.evaluate((button: HTMLButtonElement) => {
    button.click();
    button.click();
  });
  await expect(dialog.getByRole('status')).toContainText('Thanh toán thành công');
  expect(fixture.state.checkoutRequests).toBe(2);
  expect(fixture.state.orders).toBe(1);
  expect(fixture.state.debits).toBe(1);
  expect(fixture.state.soldItems).toBe(1);
  expect(fixture.state.idempotencyKeys.size).toBe(1);

  await dialog.getByRole('button', { name: 'Đóng', exact: true }).last().click();
  await page.goto('/orders');
  await page.getByRole('link', { name: 'Xem chi tiết' }).click();
  await page.getByRole('button', { name: 'Hiển thị thông tin bàn giao' }).click();
  await expect(page.getByText(fixture.deliverySecret, { exact: true })).toBeVisible();

  const storageWhileVisible = await page.evaluate(() => ({
    local: JSON.stringify({ ...localStorage }),
    session: JSON.stringify({ ...sessionStorage }),
  }));
  expect(storageWhileVisible.local).not.toContain(fixture.deliverySecret);
  expect(storageWhileVisible.session).not.toContain(fixture.deliverySecret);

  await page.goto('/orders');
  await expect(page.getByText(fixture.deliverySecret, { exact: true })).toHaveCount(0);
  await page.getByRole('link', { name: 'Xem chi tiết' }).click();
  await page.getByRole('button', { name: 'Hiển thị thông tin bàn giao' }).click();
  await expect(page.getByText(fixture.deliverySecret, { exact: true })).toBeVisible();
  await page.getByRole('button', { name: 'Ẩn lại' }).click();
  await expect(page.getByText(fixture.deliverySecret, { exact: true })).toHaveCount(0);

  fixture.actAsStranger();
  await page.goto(`/orders/${fixture.orderId}`);
  await expect(page.getByRole('heading', { name: 'Không tìm thấy đơn hàng' })).toBeVisible();
  const forgedReveal = await page.evaluate(
    async ({ orderId, itemId }) => {
      const csrf = document.cookie
        .split(';')
        .map((part) => part.trim())
        .find((part) => part.startsWith('XSRF-TOKEN='))
        ?.slice('XSRF-TOKEN='.length);
      const response = await fetch(`/api/v1/orders/${orderId}/items/${itemId}/reveal`, {
        method: 'POST',
        headers: csrf ? { 'X-XSRF-TOKEN': decodeURIComponent(csrf) } : undefined,
      });
      return { status: response.status, body: await response.text() };
    },
    { orderId: fixture.orderId, itemId: fixture.itemId }
  );
  expect(forgedReveal.status).toBe(404);
  expect(forgedReveal.body).not.toContain(fixture.deliverySecret);
  const missingReveal = await page.evaluate(async () => {
    const csrf = document.cookie
      .split(';')
      .map((part) => part.trim())
      .find((part) => part.startsWith('XSRF-TOKEN='))
      ?.slice('XSRF-TOKEN='.length);
    const response = await fetch(
      '/api/v1/orders/50000000-0000-0000-0000-000000000001/items/60000000-0000-0000-0000-000000000001/reveal',
      {
        method: 'POST',
        headers: csrf ? { 'X-XSRF-TOKEN': decodeURIComponent(csrf) } : undefined,
      }
    );
    return { status: response.status, body: await response.text() };
  });
  expect(missingReveal).toEqual(forgedReveal);
  expect(fixture.state.forbiddenDetailRequests).toBeGreaterThan(0);
  expect(fixture.state.forbiddenRevealRequests).toBe(1);

  const finalBrowserState = await page.evaluate(() => ({
    body: document.body.textContent ?? '',
    local: JSON.stringify({ ...localStorage }),
    session: JSON.stringify({ ...sessionStorage }),
  }));
  expect(JSON.stringify(finalBrowserState)).not.toContain(fixture.deliverySecret);
  expect(browserErrors).toEqual([]);
  expect(unexpectedHttpErrors).toEqual([]);
});
