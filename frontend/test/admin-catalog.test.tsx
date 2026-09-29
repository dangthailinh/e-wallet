import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import AdminProductsPage from '@/src/app/admin/products/page';
import {
  changeCategoryStatus,
  changeProductStatus,
  createCategory,
  createProduct,
  listAdminCategories,
  listAdminProducts,
  updateCategory,
  updateProduct,
} from '@/lib/api/admin-catalog';
import { ApiError } from '@/lib/api/client';

vi.mock('@/lib/api/admin-catalog', () => ({
  changeCategoryStatus: vi.fn(),
  changeProductStatus: vi.fn(),
  createCategory: vi.fn(),
  createProduct: vi.fn(),
  listAdminCategories: vi.fn(),
  listAdminProducts: vi.fn(),
  updateCategory: vi.fn(),
  updateProduct: vi.fn(),
}));

const category = {
  id: 'fa560a3d-660c-4e91-a7a9-5c5553fb4b87',
  name: 'Gói API',
  slug: 'goi-api',
  displayOrder: 1,
  status: 'ACTIVE' as const,
  createdAt: '2026-09-18T00:00:00Z',
  updatedAt: '2026-09-18T00:00:00Z',
  version: 0,
};

const product = {
  id: 'ad3a7917-196d-4f6e-9235-03f36bca7caf',
  category: { id: category.id, name: category.name, slug: category.slug },
  name: 'Codex 10M',
  slug: 'codex-10m',
  description: 'Gói API giao tự động.',
  priceVnd: 50_000,
  status: 'ACTIVE' as const,
  imageUrl: null,
  createdAt: '2026-09-18T00:00:00Z',
  updatedAt: '2026-09-18T00:00:00Z',
  version: 0,
};

const page = { number: 0, size: 20, totalItems: 1, totalPages: 1, hasNext: false, hasPrevious: false };

describe('admin catalog', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(listAdminCategories).mockResolvedValue({ items: [category], page: { ...page, size: 100 } });
    vi.mocked(listAdminProducts).mockResolvedValue({ items: [product], page });
  });

  it('lists products with status and creates a product using an integer VND payload', async () => {
    vi.mocked(createProduct).mockResolvedValue({ ...product, id: 'bd3a7917-196d-4f6e-9235-03f36bca7caf' });
    render(<AdminProductsPage />);

    expect(await screen.findByText('Codex 10M')).toBeVisible();
    expect(screen.getByText('Đang bán')).toBeVisible();

    fireEvent.click(screen.getByRole('button', { name: 'Thêm sản phẩm' }));
    fireEvent.change(screen.getByLabelText('Tên sản phẩm'), { target: { value: 'Claude API 10M' } });
    fireEvent.change(screen.getByLabelText('Slug sản phẩm'), { target: { value: 'claude-api-10m' } });
    fireEvent.change(screen.getByLabelText('Mô tả sản phẩm'), { target: { value: 'Gói API Claude giao tự động.' } });
    fireEvent.change(screen.getByLabelText('Giá bán VND'), { target: { value: '120000' } });
    fireEvent.click(screen.getByRole('button', { name: 'Lưu sản phẩm' }));

    await waitFor(() => expect(createProduct).toHaveBeenCalledWith({
      categoryId: category.id,
      name: 'Claude API 10M',
      slug: 'claude-api-10m',
      description: 'Gói API Claude giao tự động.',
      priceVnd: 120_000,
      status: 'ACTIVE',
      imageUrl: null,
    }));
  });

  it('validates fields before sending and can edit or deactivate a product', async () => {
    vi.mocked(updateProduct).mockResolvedValue({ ...product, name: 'Codex 20M' });
    vi.mocked(changeProductStatus).mockResolvedValue({ ...product, status: 'INACTIVE' });
    render(<AdminProductsPage />);

    await screen.findByText('Codex 10M');
    fireEvent.click(screen.getByRole('button', { name: 'Thêm sản phẩm' }));
    fireEvent.change(screen.getByLabelText('Tên sản phẩm'), { target: { value: 'Sản phẩm lỗi' } });
    fireEvent.change(screen.getByLabelText('Slug sản phẩm'), { target: { value: 'Slug Không Hợp Lệ' } });
    fireEvent.change(screen.getByLabelText('Mô tả sản phẩm'), { target: { value: 'Mô tả' } });
    fireEvent.change(screen.getByLabelText('Giá bán VND'), { target: { value: '50.000' } });
    fireEvent.click(screen.getByRole('button', { name: 'Lưu sản phẩm' }));

    expect(await screen.findByText('Slug chỉ gồm chữ thường, số và dấu gạch ngang.')).toBeVisible();
    expect(screen.getByText('Giá phải là số nguyên VND dương, không chứa dấu phân cách.')).toBeVisible();
    expect(createProduct).not.toHaveBeenCalled();

    fireEvent.click(screen.getByRole('button', { name: 'Hủy' }));
    fireEvent.click(screen.getByRole('button', { name: 'Sửa Codex 10M' }));
    fireEvent.change(screen.getByLabelText('Tên sản phẩm'), { target: { value: 'Codex 20M' } });
    fireEvent.click(screen.getByRole('button', { name: 'Lưu sản phẩm' }));
    await waitFor(() => expect(updateProduct).toHaveBeenCalledWith(product.id, expect.objectContaining({
      name: 'Codex 20M', priceVnd: 50_000,
    })));

    fireEvent.click(screen.getByRole('button', { name: 'Ngừng bán Codex 10M' }));
    await waitFor(() => expect(changeProductStatus).toHaveBeenCalledWith(product.id, 'INACTIVE'));
  });

  it('creates a validated category for product assignment', async () => {
    vi.mocked(createCategory).mockResolvedValue({
      ...category,
      id: 'ca560a3d-660c-4e91-a7a9-5c5553fb4b87',
      name: 'Giải trí',
      slug: 'giai-tri',
      displayOrder: 2,
    });
    render(<AdminProductsPage />);

    await screen.findByText('Codex 10M');
    fireEvent.click(screen.getByRole('button', { name: 'Thêm danh mục' }));
    fireEvent.change(screen.getByLabelText('Tên danh mục'), { target: { value: 'Giải trí' } });
    fireEvent.change(screen.getByLabelText('Slug danh mục'), { target: { value: 'giai-tri' } });
    fireEvent.change(screen.getByLabelText('Thứ tự'), { target: { value: '2' } });
    fireEvent.click(screen.getByRole('button', { name: 'Lưu danh mục' }));

    await waitFor(() => expect(createCategory).toHaveBeenCalledWith({
      name: 'Giải trí', slug: 'giai-tri', displayOrder: 2,
    }));
  });

  it('edits and hides a category while keeping product rows in sync', async () => {
    vi.mocked(updateCategory).mockResolvedValue({ ...category, name: 'Gói AI' });
    vi.mocked(changeCategoryStatus).mockResolvedValue({ ...category, name: 'Gói AI', status: 'INACTIVE' });
    render(<AdminProductsPage />);

    await screen.findByText('Codex 10M');
    fireEvent.click(screen.getByRole('button', { name: 'Sửa danh mục Gói API' }));
    fireEvent.change(screen.getByLabelText('Tên danh mục'), { target: { value: 'Gói AI' } });
    fireEvent.click(screen.getByRole('button', { name: 'Lưu danh mục' }));

    await waitFor(() => expect(updateCategory).toHaveBeenCalledWith(category.id, {
      name: 'Gói AI', slug: 'goi-api', displayOrder: 1,
    }));
    await waitFor(() => expect(screen.queryByText('Gói API')).not.toBeInTheDocument());

    fireEvent.click(screen.getByRole('button', { name: 'Ẩn danh mục Gói AI' }));
    await waitFor(() => expect(changeCategoryStatus).toHaveBeenCalledWith(category.id, 'INACTIVE'));
    expect(await screen.findByText('Đã ẩn')).toBeVisible();
  });

  it('shows an access-denied state for customer accounts', async () => {
    vi.mocked(listAdminProducts).mockRejectedValue(new ApiError(403));
    render(<AdminProductsPage />);

    expect(await screen.findByRole('alert')).toHaveTextContent('không có quyền quản lý catalog');
    expect(createProduct).not.toHaveBeenCalled();
  });
});
