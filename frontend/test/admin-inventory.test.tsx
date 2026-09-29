import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import AdminInventoryPage from '@/src/app/admin/inventory/page';
import {
  importInventory,
  listAdminProducts,
  listInventory,
} from '@/lib/api/admin-inventory';
import { ApiError } from '@/lib/api/client';

vi.mock('@/lib/api/admin-inventory', () => ({
  importInventory: vi.fn(),
  listAdminProducts: vi.fn(),
  listInventory: vi.fn(),
  changeInventoryStatus: vi.fn(),
}));

const product = {
  id: 'ad3a7917-196d-4f6e-9235-03f36bca7caf',
  category: { id: 'fa560a3d-660c-4e91-a7a9-5c5553fb4b87', name: 'Gói API', slug: 'api-packages' },
  name: 'Codex 10M',
  slug: 'codex-10m',
  priceVnd: 50_000,
  imageUrl: null,
  availableCount: 1,
};

const inventory = {
  productId: product.id,
  counts: { AVAILABLE: 1, RESERVED: 0, SOLD: 0, DISABLED: 0 },
  items: [
    {
      id: 'bd3a7917-196d-4f6e-9235-03f36bca7caf',
      productId: product.id,
      status: 'AVAILABLE' as const,
      checksumSuffix: 'a1b2c3',
      keyVersion: 1,
      createdAt: '2026-09-18T01:00:00Z',
      updatedAt: '2026-09-18T01:00:00Z',
    },
  ],
};

describe('admin inventory', () => {
  beforeEach(() => {
    vi.mocked(listAdminProducts).mockReset();
    vi.mocked(listInventory).mockReset();
    vi.mocked(importInventory).mockReset();
    vi.mocked(listAdminProducts).mockResolvedValue([product]);
    vi.mocked(listInventory).mockResolvedValue(inventory);
  });

  it('shows masked metadata and clears plaintext immediately after import', async () => {
    vi.mocked(importInventory).mockResolvedValue({
      requested: 1,
      imported: 1,
      results: [{ row: 1, status: 'IMPORTED', inventoryItemId: inventory.items[0]!.id, code: null, detail: null }],
    });
    render(<AdminInventoryPage />);

    expect(await screen.findByText('••••a1b2c3')).toBeVisible();
    expect(screen.getByText('Khóa v1')).toBeVisible();
    expect(screen.getByText((_, element) => element?.textContent === '1 khả dụng')).toBeVisible();
    expect(screen.queryByText(/ciphertext/i)).not.toBeInTheDocument();

    const textarea = screen.getByRole('textbox', { name: 'Mỗi dòng là một tài khoản, mã hoặc API key' });
    fireEvent.change(textarea, { target: { value: 'secret-that-must-disappear' } });
    fireEvent.click(screen.getByRole('button', { name: 'Mã hóa và nhập kho' }));

    await waitFor(() => expect(importInventory).toHaveBeenCalledWith(product.id, ['secret-that-must-disappear']));
    expect(textarea).toHaveValue('');
    expect(await screen.findByText('Dòng 1: Đã nhập')).toBeVisible();
    expect(screen.queryByText('secret-that-must-disappear')).not.toBeInTheDocument();
  });

  it('shows an access-denied state for non-admin users', async () => {
    vi.mocked(listAdminProducts).mockRejectedValue(new ApiError(403));

    render(<AdminInventoryPage />);

    expect(await screen.findByRole('alert')).toHaveTextContent('không có quyền truy cập');
    expect(listInventory).not.toHaveBeenCalled();
  });
});
