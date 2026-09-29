// @vitest-environment node
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { getProduct, listProducts } from '@/lib/api/catalog';
import { ApiError } from '@/lib/api/errors';

const product = {
  id: 'ad3a7917-196d-4f6e-9235-03f36bca7caf',
  category: {
    id: 'fa560a3d-660c-4e91-a7a9-5c5553fb4b87',
    name: 'Tài khoản AI',
    slug: 'ai-accounts',
  },
  name: 'ChatGPT Plus',
  slug: 'chatgpt-plus',
  priceVnd: 150_000,
  imageUrl: null,
  availableCount: 4,
};

describe('server catalog API', () => {
  beforeEach(() => {
    vi.unstubAllGlobals();
    vi.stubEnv('BACKEND_ORIGIN', 'http://localhost:8080');
  });

  it('encodes filters and returns a validated product page', async () => {
    const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      void input;
      void init;
      return Response.json({
        items: [product],
        page: {
          number: 1,
          size: 12,
          totalItems: 13,
          totalPages: 2,
          hasNext: false,
          hasPrevious: true,
        },
      });
    });
    vi.stubGlobal('fetch', fetchMock);

    await expect(
      listProducts({
        q: 'GPT & AI',
        category: 'ai-accounts',
        availability: 'in-stock',
        sort: 'price-desc',
        page: 1,
        size: 12,
      })
    ).resolves.toMatchObject({ items: [{ slug: 'chatgpt-plus' }], page: { totalItems: 13 } });

    const requestedUrl = new URL(String(fetchMock.mock.calls[0]?.[0]));
    expect(requestedUrl.origin).toBe('http://localhost:8080');
    expect(requestedUrl.pathname).toBe('/api/v1/products');
    expect(requestedUrl.searchParams.get('q')).toBe('GPT & AI');
    expect(requestedUrl.searchParams.get('category')).toBe('ai-accounts');
    expect(fetchMock.mock.calls[0]?.[1]).toMatchObject({ cache: 'no-store' });
  });

  it('accepts canonical PostgreSQL UUIDs used by the demo catalog', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () =>
        Response.json({
          items: [
            {
              ...product,
              id: '20000000-0000-0000-0000-000000000001',
              category: { ...product.category, id: '10000000-0000-0000-0000-000000000001' },
            },
          ],
          page: {
            number: 0,
            size: 20,
            totalItems: 1,
            totalPages: 1,
            hasNext: false,
            hasPrevious: false,
          },
        })
      )
    );

    await expect(listProducts()).resolves.toMatchObject({ items: [{ slug: 'chatgpt-plus' }] });
  });

  it('maps Spring problem details to a stable API error', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () =>
        Response.json(
          {
            code: 'INVALID_CATALOG_QUERY',
            detail: 'Catalog query parameters are invalid.',
          },
          { status: 400, headers: { 'Content-Type': 'application/problem+json' } }
        )
      )
    );

    await expect(listProducts({ sort: 'price-desc' })).rejects.toMatchObject({
      status: 400,
      code: 'INVALID_CATALOG_QUERY',
      message: 'Catalog query parameters are invalid.',
    });
  });

  it('rejects malformed upstream JSON instead of trusting its shape', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => Response.json({ items: [{ priceVnd: 'free' }] }))
    );

    await expect(listProducts()).rejects.toMatchObject({
      status: 502,
      code: 'INVALID_UPSTREAM_RESPONSE',
    } satisfies Partial<ApiError>);
  });

  it('validates product details and rejects unsafe slugs before fetching', async () => {
    const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      void input;
      void init;
      return Response.json({
        ...product,
        description: 'Tài khoản giao tự động.',
        createdAt: '2026-09-18T00:00:00Z',
        updatedAt: '2026-09-18T00:00:00Z',
      });
    });
    vi.stubGlobal('fetch', fetchMock);

    await expect(getProduct('chatgpt-plus')).resolves.toMatchObject({ description: 'Tài khoản giao tự động.' });
    await expect(getProduct('../admin')).rejects.toMatchObject({ status: 400, code: 'INVALID_PRODUCT_SLUG' });
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });
});
