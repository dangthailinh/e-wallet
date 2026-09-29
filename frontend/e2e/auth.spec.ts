import { expect, test, type BrowserContext, type Page, type Route } from '@playwright/test';

const session = {
  id: 7,
  username: 'buyer',
  firstName: 'Test',
  lastName: 'Buyer',
  roles: ['ROLE_USER'],
};

test.describe('authentication', () => {
  test('signup, login, protected navigation and logout use cookies without Web Storage tokens', async ({
    context,
    page,
  }) => {
    const state = { authenticated: false, registered: false };
    await mockAuthenticationApi(page, state);

    await page.goto('/signup');
    await page.getByLabel('Họ', { exact: true }).fill('Nguyen');
    await page.getByLabel('Tên', { exact: true }).fill('Anh');
    await page.getByLabel('Tên đăng nhập', { exact: true }).fill('buyer');
    await page.getByLabel('Email', { exact: true }).fill('buyer@example.com');
    await page.getByLabel('Mật khẩu', { exact: true }).fill('correct horse battery staple');
    await page.getByRole('button', { name: 'Tạo tài khoản' }).click();

    await expect(page).toHaveURL(/\/login\?registered=1$/);
    expect(state.registered).toBe(true);

    await page.getByLabel('Tên đăng nhập', { exact: true }).fill('buyer');
    await page.getByLabel('Mật khẩu', { exact: true }).fill('correct horse battery staple');
    await page.getByRole('button', { name: 'Đăng nhập', exact: true }).click();

    await expect(page).toHaveURL('/');
    await expect(page.getByText('Test', { exact: true })).toBeVisible();
    await expect(page.getByRole('button', { name: 'Đăng xuất' })).toBeVisible();

    const authCookies = (await context.cookies()).filter((cookie) => cookie.name.startsWith('EW_'));
    expect(authCookies).toEqual(
      expect.arrayContaining([
        expect.objectContaining({ name: 'EW_ACCESS', httpOnly: true, sameSite: 'Lax', path: '/api/v1' }),
        expect.objectContaining({ name: 'EW_REFRESH', httpOnly: true, sameSite: 'Lax', path: '/api/v1/auth' }),
      ])
    );

    const browserStorage = await page.evaluate(() => ({
      local: Object.entries(localStorage),
      session: Object.entries(sessionStorage),
    }));
    expect(JSON.stringify(browserStorage)).not.toMatch(/EW_ACCESS|EW_REFRESH|e2e-access-token|e2e-refresh-token/i);

    // The accessible name changes to "Đang thoát…" as soon as the request
    // starts. Use the stable class so the locator is not detached during the
    // intentional re-render.
    const logoutButton = page.locator('button.session-logout');
    await expect(logoutButton).toBeEnabled();
    await logoutButton.click();
    await expect(page.getByRole('link', { name: 'Đăng nhập', exact: true }).first()).toBeVisible();
    expect((await context.cookies()).filter((cookie) => cookie.name.startsWith('EW_'))).toEqual([]);
  });

  test('authentication restores a session by rotating the refresh cookie after me returns 401', async ({
    context,
    page,
  }) => {
    const state = { authenticated: false, registered: true, refreshAvailable: true, refreshCalls: 0 };
    await context.addCookies([
      {
        name: 'EW_REFRESH',
        value: 'e2e-refresh-token-1',
        domain: '127.0.0.1',
        path: '/api/v1/auth',
        httpOnly: true,
        secure: false,
        sameSite: 'Lax',
      },
    ]);
    await mockAuthenticationApi(page, state);

    await page.goto('/');

    await expect(page.getByText('Test', { exact: true })).toBeVisible();
    expect(state.refreshCalls).toBe(1);
    const refreshCookie = (await context.cookies()).find((cookie) => cookie.name === 'EW_REFRESH');
    expect(refreshCookie?.value).toBe('e2e-refresh-token-2');
    expect(refreshCookie?.httpOnly).toBe(true);
  });
});

type AuthState = {
  authenticated: boolean;
  registered: boolean;
  refreshAvailable?: boolean;
  refreshCalls?: number;
};

async function mockAuthenticationApi(page: Page, state: AuthState) {
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
  await page.route('**/api/v1/auth/**', async (route) => {
    const request = route.request();
    const pathname = new URL(request.url()).pathname;
    const context = page.context();

    if (pathname.endsWith('/csrf') && request.method() === 'GET') {
      await context.addCookies([
        {
          name: 'XSRF-TOKEN',
          value: 'e2e-csrf-token',
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

    if (request.method() !== 'GET') {
      expect(request.headers()['x-xsrf-token']).toBe('e2e-csrf-token');
      expect(request.headers().authorization).toBeUndefined();
    }

    if (pathname.endsWith('/signup') && request.method() === 'POST') {
      const body = request.postDataJSON() as Record<string, unknown>;
      expect(body).toMatchObject({ username: 'buyer', email: 'buyer@example.com' });
      expect(body).not.toHaveProperty('id');
      expect(body).not.toHaveProperty('roles');
      state.registered = true;
      await route.fulfill({ status: 201, contentType: 'application/json', body: JSON.stringify({ id: 7 }) });
      return;
    }

    if (pathname.endsWith('/login') && request.method() === 'POST') {
      const body = request.postDataJSON() as Record<string, unknown>;
      expect(body).toEqual({ username: 'buyer', password: 'correct horse battery staple' });
      state.authenticated = true;
      await setAuthCookies(context, 'e2e-access-token', 'e2e-refresh-token');
      await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(session) });
      return;
    }

    if (pathname.endsWith('/me') && request.method() === 'GET') {
      await json(route, state.authenticated ? 200 : 401, state.authenticated ? session : authenticationProblem());
      return;
    }

    if (pathname.endsWith('/refresh') && request.method() === 'POST') {
      state.refreshCalls = (state.refreshCalls ?? 0) + 1;
      if (!state.refreshAvailable) {
        await json(route, 401, authenticationProblem());
        return;
      }
      state.authenticated = true;
      state.refreshAvailable = false;
      await setAuthCookies(context, 'e2e-access-token-2', 'e2e-refresh-token-2');
      await json(route, 200, session);
      return;
    }

    if (pathname.endsWith('/logout') && request.method() === 'POST') {
      state.authenticated = false;
      await context.clearCookies();
      await route.fulfill({ status: 204 });
      return;
    }

    await route.abort('failed');
  });
}

async function setAuthCookies(context: BrowserContext, accessToken: string, refreshToken: string) {
  await context.addCookies([
    {
      name: 'EW_ACCESS',
      value: accessToken,
      domain: '127.0.0.1',
      path: '/api/v1',
      httpOnly: true,
      secure: false,
      sameSite: 'Lax',
    },
    {
      name: 'EW_REFRESH',
      value: refreshToken,
      domain: '127.0.0.1',
      path: '/api/v1/auth',
      httpOnly: true,
      secure: false,
      sameSite: 'Lax',
    },
  ]);
}

async function json(route: Route, status: number, body: unknown) {
  await route.fulfill({
    status,
    contentType: status >= 400 ? 'application/problem+json' : 'application/json',
    body: JSON.stringify(body),
  });
}

function authenticationProblem() {
  return {
    type: 'urn:problem:authentication-required',
    title: 'Unauthorized',
    status: 401,
    detail: 'Authentication is required.',
    code: 'AUTHENTICATION_REQUIRED',
  };
}
