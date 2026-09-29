import { randomUUID } from 'node:crypto';

import { expect, type Page, type Route } from '@playwright/test';

const product = {
  id: '20000000-0000-0000-0000-000000000006',
  name: 'Codex 10M credits - 1 ngày',
  slug: 'codex-10m-1-day',
  priceVnd: 50_000,
};

const orderId = '30000000-0000-0000-0000-000000000001';
const itemId = '40000000-0000-0000-0000-000000000001';
const createdAt = '2026-09-24T01:00:00Z';

type Actor = 'buyer' | 'stranger';

export async function installPurchaseFixture(page: Page) {
  const deliverySecret = `E2E-${randomUUID()}`;
  const credentials = { username: 'purchase-buyer', password: 'browser-only-password' };
  const state = {
    actor: 'buyer' as Actor,
    authenticated: false,
    balanceVnd: 125_000,
    checkoutRequests: 0,
    orders: 0,
    debits: 0,
    soldItems: 0,
    forbiddenDetailRequests: 0,
    forbiddenRevealRequests: 0,
    idempotencyKeys: new Set<string>(),
  };
  const ordersByKey = new Set<string>();

  await page.route('**/api/v1/auth/**', async (route) => {
    const request = route.request();
    const pathname = new URL(request.url()).pathname;

    if (pathname.endsWith('/csrf') && request.method() === 'GET') {
      await page.context().addCookies([
        {
          name: 'XSRF-TOKEN',
          value: 'purchase-e2e-csrf',
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

    if (pathname.endsWith('/login') && request.method() === 'POST') {
      expect(request.headers()['x-xsrf-token']).toBe('purchase-e2e-csrf');
      expect(request.postDataJSON()).toEqual(credentials);
      state.authenticated = true;
      state.actor = 'buyer';
      await json(route, 200, sessionFor(state.actor));
      return;
    }

    if (pathname.endsWith('/me') && request.method() === 'GET') {
      await json(route, state.authenticated ? 200 : 401, state.authenticated ? sessionFor(state.actor) : problem(401));
      return;
    }

    if (pathname.endsWith('/refresh') && request.method() === 'POST') {
      await json(route, 401, problem(401));
      return;
    }

    await route.abort('failed');
  });

  await page.route('**/api/v1/wallet**', async (route) => {
    const request = route.request();
    const pathname = new URL(request.url()).pathname;
    if (pathname === '/api/v1/wallet' && request.method() === 'GET') {
      await json(route, 200, { walletId: 7001, balanceVnd: state.balanceVnd, currency: 'VND' });
      return;
    }
    if (pathname === '/api/v1/wallet/entries' && request.method() === 'GET') {
      await json(route, 200, { items: [], page: 0, size: 5, totalItems: 0, totalPages: 0 });
      return;
    }
    await route.abort('failed');
  });

  await page.route('**/api/v1/orders**', async (route) => {
    const request = route.request();
    const pathname = new URL(request.url()).pathname;

    if (pathname === '/api/v1/orders' && request.method() === 'POST') {
      state.checkoutRequests += 1;
      expect(request.headers()['x-xsrf-token']).toBe('purchase-e2e-csrf');
      const idempotencyKey = request.headers()['idempotency-key'];
      if (!idempotencyKey) {
        throw new Error('Checkout request is missing its idempotency key');
      }
      expect(idempotencyKey).toMatch(/^[A-Za-z0-9_-]{8,100}$/);
      expect(request.postDataJSON()).toEqual({ productId: product.id, expectedPriceVnd: product.priceVnd });
      state.idempotencyKeys.add(idempotencyKey);

      const created = !ordersByKey.has(idempotencyKey);
      if (created) {
        ordersByKey.add(idempotencyKey);
        state.orders += 1;
        state.debits += 1;
        state.soldItems += 1;
        state.balanceVnd -= product.priceVnd;
      }
      await new Promise((resolve) => setTimeout(resolve, 120));
      await json(route, created ? 201 : 200, checkoutResponse(state.balanceVnd));
      return;
    }

    if (pathname === '/api/v1/orders' && request.method() === 'GET') {
      const items = state.actor === 'buyer' && state.orders === 1 ? [orderSummary()] : [];
      await json(route, 200, {
        items,
        page: 0,
        size: 20,
        totalItems: items.length,
        totalPages: items.length === 0 ? 0 : 1,
      });
      return;
    }

    if (pathname === `/api/v1/orders/${orderId}` && request.method() === 'GET') {
      if (state.actor !== 'buyer') {
        state.forbiddenDetailRequests += 1;
        await json(route, 404, problem(404));
        return;
      }
      await json(route, 200, orderDetail());
      return;
    }

    if (pathname === `/api/v1/orders/${orderId}/items/${itemId}/reveal` && request.method() === 'POST') {
      expect(request.headers()['x-xsrf-token']).toBe('purchase-e2e-csrf');
      if (state.actor !== 'buyer') {
        state.forbiddenRevealRequests += 1;
        await json(route, 404, problem(404));
        return;
      }
      await json(route, 200, { itemId, secret: deliverySecret }, { 'Cache-Control': 'no-store' });
      return;
    }

    if (/^\/api\/v1\/orders\/[^/]+\/items\/[^/]+\/reveal$/.test(pathname) && request.method() === 'POST') {
      await json(route, 404, problem(404));
      return;
    }

    await route.abort('failed');
  });

  return {
    credentials,
    deliverySecret,
    itemId,
    orderId,
    product,
    state,
    actAsStranger() {
      state.actor = 'stranger';
    },
  };
}

function sessionFor(actor: Actor) {
  return actor === 'buyer'
    ? { id: 7, username: 'purchase-buyer', firstName: 'Test', lastName: 'Buyer', roles: ['ROLE_USER'] }
    : { id: 8, username: 'stranger', firstName: 'Other', lastName: 'Buyer', roles: ['ROLE_USER'] };
}

function checkoutResponse(walletBalanceVnd: number) {
  return {
    orderId,
    itemId,
    productId: product.id,
    productName: product.name,
    totalVnd: product.priceVnd,
    status: 'COMPLETED',
    createdAt,
    walletBalanceVnd,
  };
}

function orderSummary() {
  return { orderId, status: 'COMPLETED', totalVnd: product.priceVnd, createdAt };
}

function orderDetail() {
  return {
    ...orderSummary(),
    items: [
      {
        itemId,
        productId: product.id,
        productName: product.name,
        unitPriceVnd: product.priceVnd,
      },
    ],
  };
}

function problem(status: 401 | 404) {
  return {
    type: status === 401 ? 'urn:problem:authentication-required' : 'urn:problem:resource-not-found',
    title: status === 401 ? 'Unauthorized' : 'Not Found',
    status,
    detail: status === 401 ? 'Authentication is required.' : 'Resource not found.',
    code: status === 401 ? 'AUTHENTICATION_REQUIRED' : 'RESOURCE_NOT_FOUND',
  };
}

async function json(route: Route, status: number, body: unknown, headers: Record<string, string> = {}) {
  await route.fulfill({
    status,
    contentType: status >= 400 ? 'application/problem+json' : 'application/json',
    headers,
    body: JSON.stringify(body),
  });
}
