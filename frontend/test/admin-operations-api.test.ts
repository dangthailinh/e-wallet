import { afterEach, describe, expect, it, vi } from 'vitest';

import { listAdminDeposits, listAdminOrders } from '@/lib/api/admin-operations';

const page = { number: 0, size: 20, totalItems: 0, totalPages: 0, hasNext: false, hasPrevious: false };

afterEach(() => vi.unstubAllGlobals());

describe('admin operations API client', () => {
  it('serializes order filters and strips fields outside the safe contract', async () => {
    const fetchMock = vi.fn(async () => Response.json({
      items: [{
        orderId: 'bd3a7917-196d-4f6e-9235-03f36bca7caf', userId: 42, status: 'COMPLETED', totalVnd: 50_000,
        createdAt: '2026-09-28T01:00:00Z', updatedAt: '2026-09-28T01:00:01Z',
        productName: 'must-not-cross-boundary', secret: 'must-not-cross-boundary',
      }], page: { ...page, totalItems: 1, totalPages: 1 },
    }));
    vi.stubGlobal('fetch', fetchMock);

    const result = await listAdminOrders({ page: 2, size: 20, status: 'FAILED', userId: 42 });

    expect(fetchMock).toHaveBeenCalledWith('/api/v1/admin/orders?page=2&size=20&status=FAILED&userId=42', expect.objectContaining({ credentials: 'include' }));
    expect(result.items[0]).not.toHaveProperty('productName');
    expect(result.items[0]).not.toHaveProperty('secret');
  });

  it('serializes deposit filters and rejects unsafe response shapes', async () => {
    const fetchMock = vi.fn(async () => Response.json({
      items: [{
        id: '1e89fbe4-c243-40f8-8458-125afcd5bde4', userId: 42, channel: 'MOMO', providerCode: 'MOMO',
        amountVnd: 100_000, currency: 'VND', status: 'REVIEW', expiresAt: '2026-09-28T02:00:00Z', paidAt: null,
        createdAt: '2026-09-28T01:00:00Z', updatedAt: '2026-09-28T01:00:01Z', needsReconciliationReview: true,
        merchantReference: 'hidden', providerTransactionId: 'hidden', instructions: { redirectUrl: 'https://hidden.test' },
      }], page,
    }));
    vi.stubGlobal('fetch', fetchMock);

    const result = await listAdminDeposits({ status: 'REVIEW', channel: 'MOMO', userId: 42 });

    expect(fetchMock).toHaveBeenCalledWith('/api/v1/admin/deposits?page=0&size=20&status=REVIEW&channel=MOMO&userId=42', expect.objectContaining({ credentials: 'include' }));
    expect(result.items[0]).not.toHaveProperty('merchantReference');
    expect(result.items[0]).not.toHaveProperty('providerTransactionId');
    expect(result.items[0]).not.toHaveProperty('instructions');

    fetchMock.mockResolvedValueOnce(Response.json({ items: [{ ...result.items[0], needsReconciliationReview: 'yes' }], page }));
    await expect(listAdminDeposits()).rejects.toThrow('Invalid admin operations response');
  });
});
