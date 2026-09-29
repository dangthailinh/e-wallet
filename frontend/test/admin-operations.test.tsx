import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import AdminOrdersPage from '@/src/app/admin/orders/page';
import AdminDepositsPage from '@/src/app/admin/deposits/page';
import { ApiError } from '@/lib/api/client';
import {
  listAdminDeposits,
  listAdminOrders,
} from '@/lib/api/admin-operations';

vi.mock('@/lib/api/admin-operations', () => ({
  listAdminDeposits: vi.fn(),
  listAdminOrders: vi.fn(),
}));

const page = { number: 0, size: 20, totalItems: 1, totalPages: 1, hasNext: false, hasPrevious: false };
const order = {
  orderId: 'bd3a7917-196d-4f6e-9235-03f36bca7caf',
  userId: 42,
  status: 'COMPLETED' as const,
  totalVnd: 50_000,
  createdAt: '2026-09-28T01:00:00Z',
  updatedAt: '2026-09-28T01:00:01Z',
};
const deposit = {
  id: '1e89fbe4-c243-40f8-8458-125afcd5bde4',
  userId: 42,
  channel: 'MOMO' as const,
  providerCode: 'MOMO',
  amountVnd: 100_000,
  currency: 'VND' as const,
  status: 'REVIEW' as const,
  expiresAt: '2026-09-28T02:00:00Z',
  paidAt: null,
  createdAt: '2026-09-28T01:00:00Z',
  updatedAt: '2026-09-28T01:00:01Z',
  needsReconciliationReview: true,
};

describe('admin operations', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    window.history.replaceState({}, '', '/admin/orders?status=FAILED&userId=42&page=1');
    vi.mocked(listAdminOrders).mockResolvedValue({ items: [order], page });
    vi.mocked(listAdminDeposits).mockResolvedValue({ items: [deposit], page });
  });

  it('loads order filters from the URL, shows safe metadata, and updates pagination in the URL', async () => {
    vi.mocked(listAdminOrders).mockResolvedValueOnce({
      items: [order],
      page: { ...page, number: 1, totalPages: 2, hasPrevious: true, hasNext: true },
    });
    render(<AdminOrdersPage />);

    expect(await screen.findByText('BD3A7917')).toBeVisible();
    expect(screen.getByText('50.000 ₫')).toBeVisible();
    expect(screen.getByText('Hoàn tất', { selector: 'span' })).toBeVisible();
    expect(listAdminOrders).toHaveBeenCalledWith({ page: 1, size: 20, status: 'FAILED', userId: 42 });
    expect(screen.queryByText(/merchant|secret|api key|plaintext/i)).not.toBeInTheDocument();

    vi.mocked(listAdminOrders).mockResolvedValueOnce({
      items: [{ ...order, orderId: 'cd3a7917-196d-4f6e-9235-03f36bca7caf' }],
      page: { ...page, number: 1, totalPages: 2, hasPrevious: true },
    });
    fireEvent.click(screen.getByRole('button', { name: 'Trang sau' }));

    await waitFor(() => expect(listAdminOrders).toHaveBeenLastCalledWith({
      page: 2,
      size: 20,
      status: 'FAILED',
      userId: 42,
    }));
    expect(window.location.search).toContain('page=2');
  });

  it('shows an access-denied state for customers on the order screen', async () => {
    vi.mocked(listAdminOrders).mockRejectedValue(new ApiError(403));

    render(<AdminOrdersPage />);

    expect(await screen.findByRole('alert')).toHaveTextContent('không có quyền truy cập');
    expect(screen.queryByText(/BD3A7917/)).not.toBeInTheDocument();
  });

  it('loads deposit filters from the URL and highlights reconciliation review without secrets', async () => {
    window.history.replaceState({}, '', '/admin/deposits?status=REVIEW&channel=MOMO&userId=42');
    render(<AdminDepositsPage />);

    expect(await screen.findByText('1E89FBE4')).toBeVisible();
    expect(screen.getByText('Cần đối soát')).toBeVisible();
    expect(screen.getByText('100.000 ₫')).toBeVisible();
    expect(listAdminDeposits).toHaveBeenCalledWith({
      page: 0,
      size: 20,
      status: 'REVIEW',
      channel: 'MOMO',
      userId: 42,
    });
    expect(screen.queryByText(/merchant|transaction|secret|api key|plaintext/i)).not.toBeInTheDocument();
  });

  it('shows an access-denied state for customers on the deposit screen', async () => {
    vi.mocked(listAdminDeposits).mockRejectedValue(new ApiError(403));

    render(<AdminDepositsPage />);

    expect(await screen.findByRole('alert')).toHaveTextContent('không có quyền truy cập');
    expect(screen.queryByText(/1E89FBE4/)).not.toBeInTheDocument();
  });
});
