import { render, screen } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import SearchPage from '@/src/app/search/page';
import { listProducts } from '@/lib/api/catalog';

vi.mock('@/lib/api/catalog', () => ({
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
  priceVnd: 50_000,
  imageUrl: null,
  availableCount: 11,
};

const catalogPage = {
  items: [product],
  page: {
    number: 1,
    size: 24,
    totalItems: 25,
    totalPages: 2,
    hasNext: false,
    hasPrevious: true,
  },
};

describe('catalog view', () => {
  beforeEach(() => {
    vi.mocked(listProducts).mockReset();
  });

  it('server-renders URL filters and preserves them when changing view', async () => {
    vi.mocked(listProducts)
      .mockResolvedValueOnce(catalogPage)
      .mockResolvedValueOnce({ ...catalogPage, items: [product] });

    render(
      await SearchPage({
        searchParams: Promise.resolve({
          q: ' Codex ',
          category: 'api-packages',
          availability: 'in-stock',
          sort: 'price-asc',
          view: 'list',
          page: '2',
        }),
      })
    );

    expect(listProducts).toHaveBeenNthCalledWith(1, {
      q: 'Codex',
      category: 'api-packages',
      availability: 'in-stock',
      sort: 'price-asc',
      page: 1,
      size: 24,
    });
    expect(screen.getByRole('searchbox', { name: 'Tìm trong danh mục' })).toHaveValue('Codex');
    expect(screen.getByRole('combobox', { name: 'Danh mục' })).toHaveValue('api-packages');
    expect(screen.getByRole('heading', { name: 'Codex 10M credits' })).toBeVisible();
    expect(screen.getByText(/50\.000\s*₫/)).toBeVisible();
    expect(screen.getByText('Còn 11')).toBeVisible();
    expect(screen.getByRole('link', { name: 'Xem dạng lưới' })).toHaveAttribute(
      'href',
      '/search?q=Codex&category=api-packages&availability=in-stock&sort=price-asc&view=grid&page=2'
    );
  });

  it('shows actionable empty and error states', async () => {
    vi.mocked(listProducts)
      .mockResolvedValueOnce({
        items: [],
        page: { number: 0, size: 24, totalItems: 0, totalPages: 0, hasNext: false, hasPrevious: false },
      })
      .mockResolvedValueOnce({ ...catalogPage, items: [product] });

    const empty = render(await SearchPage({ searchParams: Promise.resolve({ q: 'khong-co' }) }));
    expect(screen.getByRole('status')).toHaveTextContent('Không tìm thấy sản phẩm phù hợp');
    expect(screen.getByRole('link', { name: 'Xóa bộ lọc' })).toHaveAttribute('href', '/search');
    empty.unmount();

    vi.mocked(listProducts).mockReset();
    vi.mocked(listProducts).mockRejectedValueOnce(new Error('offline')).mockResolvedValueOnce({
      ...catalogPage,
      items: [product],
    });

    render(await SearchPage({ searchParams: Promise.resolve({ q: 'codex' }) }));
    expect(screen.getByRole('alert')).toHaveTextContent('Chưa thể tải danh mục');
    expect(screen.getByRole('link', { name: 'Thử lại' })).toHaveAttribute('href', '/search?q=codex');
  });
});
