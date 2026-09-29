import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import OrderDetailPage from '@/src/app/orders/[id]/page';
import OrdersPage from '@/src/app/orders/page';
import { announceSessionEnded, SESSION_ENDED_EVENT } from '@/lib/api/auth';
import { ApiError } from '@/lib/api/errors';
import { getOrder, listOrders, revealOrderItem } from '@/lib/api/orders';

vi.mock('next/navigation', () => ({
  useParams: () => ({ id: order.orderId }),
}));

vi.mock('@/lib/api/orders', () => ({
  getOrder: vi.fn(),
  listOrders: vi.fn(),
  revealOrderItem: vi.fn(),
}));

const order = {
  orderId: 'bd3a7917-196d-4f6e-9235-03f36bca7caf',
  status: 'COMPLETED' as const,
  totalVnd: 50_000,
  createdAt: '2026-09-23T01:00:00Z',
};

const detail = {
  ...order,
  items: [
    {
      itemId: 'cd3a7917-196d-4f6e-9235-03f36bca7caf',
      productId: 'ad3a7917-196d-4f6e-9235-03f36bca7caf',
      productName: 'Codex 10M credits',
      unitPriceVnd: 50_000,
    },
  ],
};

describe('order history', () => {
  beforeEach(() => {
    localStorage.clear();
    sessionStorage.clear();
    vi.mocked(listOrders).mockReset();
    vi.mocked(getOrder).mockReset();
    vi.mocked(revealOrderItem).mockReset();
    vi.mocked(listOrders).mockResolvedValue({
      items: [order],
      page: 0,
      size: 20,
      totalItems: 1,
      totalPages: 1,
    });
    vi.mocked(getOrder).mockResolvedValue(detail);
  });

  it('renders a responsive order list with a detail link', async () => {
    render(<OrdersPage />);

    expect(await screen.findByRole('heading', { name: 'Đơn hàng của anh' })).toBeVisible();
    expect(screen.getByText('50.000 ₫')).toBeVisible();
    expect(screen.getByText('Hoàn tất')).toBeVisible();
    expect(screen.getByRole('link', { name: 'Xem chi tiết' })).toHaveAttribute('href', `/orders/${order.orderId}`);
  });

  it('keeps delivery data masked until an explicit reveal, then copies and re-hides it', async () => {
    const writeText = vi.fn().mockResolvedValue(undefined);
    Object.defineProperty(navigator, 'clipboard', { configurable: true, value: { writeText } });
    vi.mocked(revealOrderItem).mockResolvedValue({ itemId: detail.items[0]!.itemId, secret: 'api-secret-value' });

    render(<OrderDetailPage />);

    expect(await screen.findByText('Codex 10M credits')).toBeVisible();
    expect(screen.getByText('••••••••••••')).toBeVisible();
    expect(revealOrderItem).not.toHaveBeenCalled();
    expect(screen.queryByText('api-secret-value')).not.toBeInTheDocument();

    const reveal = screen.getByRole('button', { name: 'Hiển thị thông tin bàn giao' });
    reveal.focus();
    fireEvent.click(reveal);
    expect(await screen.findByText('api-secret-value')).toBeVisible();
    expect(revealOrderItem).toHaveBeenCalledWith(order.orderId, detail.items[0]!.itemId);

    const copy = screen.getByRole('button', { name: 'Sao chép' });
    await waitFor(() => expect(copy).toHaveFocus());
    fireEvent.click(copy);
    await waitFor(() => expect(writeText).toHaveBeenCalledWith('api-secret-value'));
    expect(screen.getByRole('status')).toHaveTextContent('Đã sao chép');

    fireEvent.click(screen.getByRole('button', { name: 'Ẩn lại' }));
    expect(screen.queryByText('api-secret-value')).not.toBeInTheDocument();
    expect(screen.getByText('••••••••••••')).toBeVisible();
    expect(screen.getByRole('status')).toHaveTextContent('Đã ẩn thông tin bàn giao');
    await waitFor(() => expect(screen.getByRole('button', { name: 'Hiển thị thông tin bàn giao' })).toHaveFocus());
    expect(localStorage).toHaveLength(0);
    expect(sessionStorage).toHaveLength(0);
  });

  it('clears a revealed secret immediately when the session ends', async () => {
    vi.mocked(revealOrderItem).mockResolvedValue({ itemId: detail.items[0]!.itemId, secret: 'session-secret' });
    render(<OrderDetailPage />);

    await screen.findByText('Codex 10M credits');
    fireEvent.click(screen.getByRole('button', { name: 'Hiển thị thông tin bàn giao' }));
    expect(await screen.findByText('session-secret')).toBeVisible();

    announceSessionEnded();

    await waitFor(() => expect(screen.queryByText('session-secret')).not.toBeInTheDocument());
    expect(screen.getByRole('link', { name: 'Đăng nhập lại' })).toHaveAttribute('href', '/login');
    expect(screen.getByRole('alert')).toHaveTextContent('Phiên đăng nhập đã kết thúc');
  });

  it('turns an expired reveal session into a login action', async () => {
    vi.mocked(revealOrderItem).mockRejectedValueOnce(new ApiError(401, { code: 'AUTHENTICATION_REQUIRED' }));
    render(<OrderDetailPage />);

    await screen.findByText('Codex 10M credits');
    fireEvent.click(screen.getByRole('button', { name: 'Hiển thị thông tin bàn giao' }));

    const login = await screen.findByRole('link', { name: 'Đăng nhập lại' });
    expect(login).toHaveAttribute('href', '/login');
    await waitFor(() => expect(login).toHaveFocus());
    expect(screen.queryByRole('button', { name: 'Thử mở lại' })).not.toBeInTheDocument();
  });

  it('clears an in-flight reveal when another tab ends the session', async () => {
    let receiveMessage!: (event: MessageEvent<unknown>) => void;
    class FakeBroadcastChannel {
      addEventListener(_type: string, listener: (event: MessageEvent<unknown>) => void) {
        receiveMessage = listener;
      }
      removeEventListener() {}
      postMessage() {}
      close() {}
    }
    vi.stubGlobal('BroadcastChannel', FakeBroadcastChannel);
    let resolveReveal!: (value: { itemId: string; secret: string }) => void;
    vi.mocked(revealOrderItem).mockImplementationOnce(
      () =>
        new Promise((resolve) => {
          resolveReveal = resolve;
        })
    );
    render(<OrderDetailPage />);

    await screen.findByText('Codex 10M credits');
    fireEvent.click(screen.getByRole('button', { name: 'Hiển thị thông tin bàn giao' }));
    receiveMessage(new MessageEvent('message', { data: SESSION_ENDED_EVENT }));
    expect(await screen.findByRole('link', { name: 'Đăng nhập lại' })).toBeVisible();

    resolveReveal({ itemId: detail.items[0]!.itemId, secret: 'late-secret' });
    await waitFor(() => expect(screen.queryByText('late-secret')).not.toBeInTheDocument());
    expect(screen.getByText('••••••••••••')).toBeVisible();
    vi.unstubAllGlobals();
  });

  it('offers a retry without exposing a failed reveal response', async () => {
    vi.mocked(revealOrderItem).mockRejectedValueOnce(new Error('temporary failure'));
    render(<OrderDetailPage />);

    await screen.findByText('Codex 10M credits');
    fireEvent.click(screen.getByRole('button', { name: 'Hiển thị thông tin bàn giao' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Không thể mở thông tin bàn giao');
    expect(screen.getByRole('button', { name: 'Thử mở lại' })).toBeVisible();
    expect(screen.queryByText(/temporary failure/i)).not.toBeInTheDocument();
  });
});
