import { expect, test, type Page, type Route } from '@playwright/test';

const depositId = '1e89fbe4-c243-40f8-8458-125afcd5bde4';
const pendingDeposit = {
  id: depositId,
  channel: 'MOMO',
  providerCode: 'MOMO',
  merchantReference: 'DEP_MOMO_E2E_0001',
  amountVnd: 100_000,
  currency: 'VND',
  status: 'PENDING',
  expiresAt: '2099-09-26T10:30:00Z',
  instructions: { redirectUrl: 'https://test-payment.momo.vn/v2/gateway/pay?t=e2e-safe-token' },
};

test('MoMo top-up stays pending until a verified backend status becomes paid', async ({ context, page }) => {
  let statusRequests = 0;
  await installTopUpFixture(page, () => ++statusRequests);

  await page.goto('/top-up');
  await expect(page.getByText('Ví MoMo', { exact: true })).toBeVisible();
  await expect(page.getByText('Chuyển khoản ngân hàng')).toHaveCount(0);
  await page.getByLabel('Số tiền nạp').fill('100000');
  await page.getByRole('button', { name: 'Tạo yêu cầu nạp tiền' }).click();

  await expect(page.getByRole('heading', { name: 'Đang chờ thanh toán' })).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Nạp tiền thành công' })).toHaveCount(0);
  await expect(page.getByRole('link', { name: 'Mở cổng thanh toán MoMo' })).toHaveAttribute(
    'href',
    pendingDeposit.instructions.redirectUrl
  );

  await expect(page.getByRole('heading', { name: 'Nạp tiền thành công' })).toBeVisible({ timeout: 10_000 });
  expect(statusRequests).toBeGreaterThanOrEqual(2);

  const storage = await page.evaluate(() => JSON.stringify({ ...localStorage, ...sessionStorage }));
  expect(storage).not.toMatch(/secret|access.?key|signature/i);
  expect((await context.cookies()).find((cookie) => cookie.name === 'XSRF-TOKEN')?.httpOnly).toBe(false);
});

async function installTopUpFixture(page: Page, nextStatusRequest: () => number) {
  await page.route('**/api/v1/**', async (route) => {
    const request = route.request();
    const pathname = new URL(request.url()).pathname;

    if (pathname === '/api/v1/auth/csrf') {
      await page.context().addCookies([
        {
          name: 'XSRF-TOKEN',
          value: 'top-up-e2e-csrf',
          domain: '127.0.0.1',
          path: '/',
          httpOnly: false,
          secure: false,
          sameSite: 'Lax',
        },
      ]);
      await route.fulfill({ status: 204 });
      return;
    }
    if (pathname === '/api/v1/auth/me') {
      await json(route, 200, { id: 7, username: 'buyer', firstName: 'Test', lastName: 'Buyer', roles: ['ROLE_USER'] });
      return;
    }
    if (pathname === '/api/v1/wallet') {
      await json(route, 200, { walletId: 9, balanceVnd: 0, currency: 'VND' });
      return;
    }
    if (pathname === '/api/v1/deposits' && request.method() === 'POST') {
      expect(request.headers()['x-xsrf-token']).toBe('top-up-e2e-csrf');
      expect(request.postDataJSON()).toEqual({ channel: 'MOMO', amountVnd: 100_000 });
      await json(route, 201, pendingDeposit);
      return;
    }
    if (pathname === `/api/v1/deposits/${depositId}` && request.method() === 'GET') {
      const attempt = nextStatusRequest();
      await json(route, 200, {
        ...pendingDeposit,
        status: attempt >= 2 ? 'PAID' : 'PENDING',
        paidAt: attempt >= 2 ? '2026-09-26T10:05:00Z' : undefined,
        instructions: undefined,
      });
      return;
    }

    await route.abort('failed');
  });
}

async function json(route: Route, status: number, body: unknown) {
  await route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) });
}
