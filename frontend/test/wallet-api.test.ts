import { beforeEach, describe, expect, it, vi } from 'vitest';

import { getCurrentWallet, getWalletEntries } from '@/lib/api/wallet';

describe('wallet API client', () => {
  beforeEach(() => vi.unstubAllGlobals());

  it('uses only current-user endpoints with browser cookies and no cache', async () => {
    const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      expect(init).toMatchObject({ credentials: 'include', cache: 'no-store', method: 'GET' });
      if (String(input).endsWith('/wallet')) {
        return Response.json({ walletId: 9, balanceVnd: 125_000, currency: 'VND' });
      }
      return Response.json({ items: [], page: 0, size: 5, totalItems: 0, totalPages: 0 });
    });
    vi.stubGlobal('fetch', fetchMock);

    await expect(getCurrentWallet()).resolves.toMatchObject({ balanceVnd: 125_000 });
    await expect(getWalletEntries(0, 5)).resolves.toMatchObject({ items: [], totalItems: 0 });

    expect(fetchMock.mock.calls.map(([input]) => String(input))).toEqual([
      '/api/v1/wallet',
      '/api/v1/wallet/entries?page=0&size=5',
    ]);
  });

  it('refuses amounts that JavaScript cannot represent exactly', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => Response.json({ walletId: 9, balanceVnd: 9_007_199_254_740_992, currency: 'VND' }))
    );
    await expect(getCurrentWallet()).rejects.toMatchObject({ code: 'WALLET_AMOUNT_UNSAFE' });
  });

  it('rejects an unsafe ledger amount instead of displaying a rounded transaction', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () =>
        Response.json({
          items: [{ amountVnd: 9_007_199_254_740_992, balanceAfterVnd: 0 }],
          page: 0,
          size: 5,
          totalItems: 1,
          totalPages: 1,
        })
      )
    );
    await expect(getWalletEntries()).rejects.toMatchObject({ code: 'WALLET_AMOUNT_UNSAFE' });
  });
});
