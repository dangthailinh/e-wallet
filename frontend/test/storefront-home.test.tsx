import { render, screen } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import HomePage from '@/src/app/page';
import { listProducts } from '@/lib/api/catalog';

vi.mock('@/lib/api/catalog', () => ({
  listProducts: vi.fn(),
}));

const catalogPage = {
  items: [
    {
      id: 'ad3a7917-196d-4f6e-9235-03f36bca7caf',
      category: {
        id: 'fa560a3d-660c-4e91-a7a9-5c5553fb4b87',
        name: 'Tài khoản AI',
        slug: 'ai-accounts',
      },
      name: 'Sản phẩm từ API',
      slug: 'san-pham-tu-api',
      priceVnd: 150_000,
      imageUrl: null,
      availableCount: 4,
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
};

describe('storefront home', () => {
  beforeEach(() => {
    vi.mocked(listProducts).mockReset();
  });

  it('renders API products, category navigation, price and stock', async () => {
    vi.mocked(listProducts).mockResolvedValue(catalogPage);

    render(await HomePage({ searchParams: Promise.resolve({}) }));

    expect(screen.getByRole('heading', { name: 'Sản phẩm từ API' })).toBeVisible();
    expect(screen.getByRole('link', { name: 'Tài khoản AI' })).toHaveAttribute(
      'href',
      '/search?category=ai-accounts'
    );
    expect(screen.getByText(/150\.000\s*₫/)).toBeVisible();
    expect(screen.getByText('Còn 4')).toBeVisible();
    expect(listProducts).toHaveBeenCalledWith({ sort: 'newest', size: 20 });
  });

  it('shows an actionable error instead of a blank page', async () => {
    vi.mocked(listProducts).mockRejectedValue(new Error('backend unavailable'));

    render(await HomePage({ searchParams: Promise.resolve({}) }));

    expect(screen.getByRole('alert')).toHaveTextContent('Chưa thể tải sản phẩm');
    expect(screen.getByRole('link', { name: 'Thử lại' })).toHaveAttribute('href', '/');
  });
});
