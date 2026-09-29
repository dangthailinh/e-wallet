import { render, screen } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import ProductPage, { generateMetadata } from '@/src/app/product/[slug]/page';
import { getProduct, listProducts } from '@/lib/api/catalog';
import { ApiError } from '@/lib/api/errors';

const navigation = vi.hoisted(() => ({
  notFound: vi.fn((): never => {
    throw new Error('NEXT_NOT_FOUND');
  }),
}));

vi.mock('next/navigation', () => ({ notFound: navigation.notFound }));
vi.mock('@/lib/api/catalog', () => ({
  getProduct: vi.fn(),
  listProducts: vi.fn(),
}));

const product = {
  id: 'ad3a7917-196d-4f6e-9235-03f36bca7caf',
  category: {
    id: 'fa560a3d-660c-4e91-a7a9-5c5553fb4b87',
    name: 'Gói API',
    slug: 'api-packages',
  },
  name: 'Codex 10M credits',
  slug: 'codex-10m-credits',
  description: 'Gói API được bàn giao an toàn trong lịch sử đơn hàng.',
  priceVnd: 50_000,
  imageUrl: null,
  availableCount: 11,
  createdAt: '2026-09-18T01:00:00Z',
  updatedAt: '2026-09-18T01:00:00Z',
};

const related = {
  ...product,
  id: 'bd3a7917-196d-4f6e-9235-03f36bca7caf',
  name: 'Codex 50M credits',
  slug: 'codex-50m-credits',
  priceVnd: 110_000,
};

describe('product detail', () => {
  beforeEach(() => {
    vi.mocked(getProduct).mockReset();
    vi.mocked(listProducts).mockReset();
    navigation.notFound.mockClear();
  });

  it('renders product, wallet purchase context, JSON-LD and related items', async () => {
    vi.mocked(getProduct).mockResolvedValue(product);
    vi.mocked(listProducts).mockResolvedValue({
      items: [product, related],
      page: { number: 0, size: 5, totalItems: 2, totalPages: 1, hasNext: false, hasPrevious: false },
    });

    const { container } = render(await ProductPage({ params: Promise.resolve({ slug: product.slug }) }));

    expect(screen.getByRole('heading', { level: 1, name: product.name })).toBeVisible();
    expect(screen.getByText(/50\.000\s*₫/)).toBeVisible();
    expect(screen.getByText('Còn 11 sản phẩm')).toBeVisible();
    expect(screen.getByText('Bảo hành và hỗ trợ')).toBeVisible();
    expect(screen.getByRole('button', { name: 'Mua bằng số dư ví' })).toBeEnabled();
    expect(screen.getByRole('heading', { name: related.name })).toBeVisible();
    expect(container.querySelector('script[type="application/ld+json"]')?.textContent).toContain('InStock');
    expect(screen.queryByText(/shipping|tax|variant/i)).not.toBeInTheDocument();
  });

  it('generates product metadata', async () => {
    vi.mocked(getProduct).mockResolvedValue(product);

    const metadata = await generateMetadata({ params: Promise.resolve({ slug: product.slug }) });

    expect(metadata.title).toContain(product.name);
    expect(metadata.description).toContain('Gói API');
  });

  it('uses the Next.js not-found boundary for a missing product', async () => {
    vi.mocked(getProduct).mockRejectedValue(new ApiError(404));

    await expect(ProductPage({ params: Promise.resolve({ slug: 'missing-product' }) })).rejects.toThrow(
      'NEXT_NOT_FOUND'
    );
    expect(navigation.notFound).toHaveBeenCalledOnce();
    expect(listProducts).not.toHaveBeenCalled();
  });
});
