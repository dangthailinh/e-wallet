import { beforeEach, describe, expect, it, vi } from 'vitest';

import { checkoutProduct, getOrder, listOrders, revealOrderItem } from '@/lib/api/orders';

const productId = 'ad3a7917-196d-4f6e-9235-03f36bca7caf';
const order = {
  orderId: 'bd3a7917-196d-4f6e-9235-03f36bca7caf',
  itemId: 'cd3a7917-196d-4f6e-9235-03f36bca7caf',
  productId,
  productName: 'Codex 10M credits',
  totalVnd: 50_000,
  status: 'COMPLETED',
  createdAt: '2026-09-23T01:00:00Z',
  walletBalanceVnd: 75_000,
  secret: 'must-not-be-read',
};

describe('orders API client', () => {
  beforeEach(() => {
    document.cookie = 'XSRF-TOKEN=; Max-Age=0; Path=/';
    vi.unstubAllGlobals();
  });

  it('sends the idempotency key and returns only the safe checkout contract', async () => {
    let checkoutRequest: RequestInit | undefined;
    vi.stubGlobal(
      'fetch',
      vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
        if (String(input).endsWith('/auth/csrf')) {
          document.cookie = 'XSRF-TOKEN=order-csrf; Path=/';
          return new Response(null, { status: 204 });
        }
        checkoutRequest = init;
        return Response.json(order);
      })
    );

    const result = await checkoutProduct(productId, 50_000, 'checkout_key_001');

    expect(new Headers(checkoutRequest?.headers).get('Idempotency-Key')).toBe('checkout_key_001');
    expect(new Headers(checkoutRequest?.headers).get('X-XSRF-TOKEN')).toBe('order-csrf');
    expect(checkoutRequest?.body).toBe(JSON.stringify({ productId, expectedPriceVnd: 50_000 }));
    expect(result).not.toHaveProperty('secret');
    expect(result.walletBalanceVnd).toBe(75_000);
  });

  it('rejects unsafe money values from the server', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async (input: RequestInfo | URL) => {
        if (String(input).endsWith('/auth/csrf')) {
          document.cookie = 'XSRF-TOKEN=order-csrf; Path=/';
          return new Response(null, { status: 204 });
        }
        return Response.json({ ...order, walletBalanceVnd: 9_007_199_254_740_992 });
      })
    );

    await expect(checkoutProduct(productId, 50_000, 'checkout_key_001')).rejects.toMatchObject({
      code: 'ORDER_RESPONSE_INVALID',
    });
  });

  it('keeps secret fields out of list and detail results', async () => {
    const summary = {
      orderId: order.orderId,
      status: order.status,
      totalVnd: order.totalVnd,
      createdAt: order.createdAt,
      secret: 'must-not-be-read',
    };
    vi.stubGlobal(
      'fetch',
      vi.fn(async (input: RequestInfo | URL) => {
        if (String(input).includes(`/${order.orderId}`)) {
          return Response.json({
            ...summary,
            items: [
              {
                itemId: order.itemId,
                productId,
                productName: order.productName,
                unitPriceVnd: order.totalVnd,
                secret: 'must-not-be-read',
              },
            ],
          });
        }
        return Response.json({ items: [summary], page: 0, size: 20, totalItems: 1, totalPages: 1 });
      })
    );

    const list = await listOrders();
    const detail = await getOrder(order.orderId);

    expect(list.items[0]).not.toHaveProperty('secret');
    expect(detail).not.toHaveProperty('secret');
    expect(detail.items[0]).not.toHaveProperty('secret');
  });

  it('fetches plaintext only through the explicit no-store reveal request', async () => {
    let revealRequest: RequestInit | undefined;
    vi.stubGlobal(
      'fetch',
      vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
        if (String(input).endsWith('/auth/csrf')) {
          document.cookie = 'XSRF-TOKEN=reveal-csrf; Path=/';
          return new Response(null, { status: 204 });
        }
        revealRequest = init;
        return Response.json({ itemId: order.itemId, secret: 'one-time-secret' });
      })
    );

    const result = await revealOrderItem(order.orderId, order.itemId);

    expect(result).toEqual({ itemId: order.itemId, secret: 'one-time-secret' });
    expect(revealRequest?.method).toBe('POST');
    expect(revealRequest?.cache).toBe('no-store');
    expect(new Headers(revealRequest?.headers).get('X-XSRF-TOKEN')).toBe('reveal-csrf');
  });

  it('rejects a reveal response for a different item', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async (input: RequestInfo | URL) => {
        if (String(input).endsWith('/auth/csrf')) {
          document.cookie = 'XSRF-TOKEN=reveal-csrf; Path=/';
          return new Response(null, { status: 204 });
        }
        return Response.json({
          itemId: 'dd3a7917-196d-4f6e-9235-03f36bca7caf',
          secret: 'wrong-item-secret',
        });
      })
    );

    await expect(revealOrderItem(order.orderId, order.itemId)).rejects.toMatchObject({
      code: 'ORDER_RESPONSE_INVALID',
    });
  });
});
