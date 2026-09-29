import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { BuyButton } from '@/components/order/buy-button';
import { checkoutProduct } from '@/lib/api/orders';
import { ApiError } from '@/lib/api/errors';
import { getCurrentWallet } from '@/lib/api/wallet';
import type { CatalogProductDetail } from '@/lib/contracts/catalog';

vi.mock('@/lib/api/orders', () => ({ checkoutProduct: vi.fn() }));
vi.mock('@/lib/api/wallet', () => ({ getCurrentWallet: vi.fn() }));

const product: CatalogProductDetail = {
  id: 'ad3a7917-196d-4f6e-9235-03f36bca7caf',
  category: { id: 'fa560a3d-660c-4e91-a7a9-5c5553fb4b87', name: 'Gói API', slug: 'api-packages' },
  name: 'Codex 10M credits',
  slug: 'codex-10m-credits',
  description: 'Gói API được bàn giao an toàn trong lịch sử đơn hàng.',
  priceVnd: 50_000,
  imageUrl: null,
  availableCount: 11,
  createdAt: '2026-09-18T01:00:00Z',
  updatedAt: '2026-09-18T01:00:00Z',
};

const completedOrder = {
  orderId: 'bd3a7917-196d-4f6e-9235-03f36bca7caf',
  itemId: 'cd3a7917-196d-4f6e-9235-03f36bca7caf',
  productId: product.id,
  productName: product.name,
  totalVnd: product.priceVnd,
  status: 'COMPLETED' as const,
  createdAt: '2026-09-23T01:00:00Z',
  walletBalanceVnd: 75_000,
};

describe('buy with wallet', () => {
  beforeEach(() => {
    sessionStorage.clear();
    vi.mocked(getCurrentWallet).mockReset();
    vi.mocked(checkoutProduct).mockReset();
    vi.mocked(getCurrentWallet).mockResolvedValue({ walletId: 9, balanceVnd: 125_000, currency: 'VND' });
    vi.mocked(checkoutProduct).mockResolvedValue(completedOrder);
  });

  it('shows the product, price and wallet balance before and after purchase', async () => {
    render(<BuyButton product={product} />);

    fireEvent.click(screen.getByRole('button', { name: 'Mua bằng số dư ví' }));
    const dialog = await screen.findByRole('dialog', { name: 'Xác nhận thanh toán' });
    expect(dialog).toHaveTextContent(product.name);
    expect(dialog).toHaveTextContent('50.000 ₫');
    expect(dialog).toHaveTextContent('125.000 ₫');
    expect(dialog).toHaveTextContent('75.000 ₫');

    fireEvent.click(screen.getByRole('button', { name: 'Xác nhận mua' }));
    expect(await screen.findByRole('status')).toHaveTextContent('Thanh toán thành công');
    expect(screen.getByText('Số dư mới')).toBeVisible();
    expect(screen.getAllByText('75.000 ₫').length).toBeGreaterThan(0);
  });

  it('reuses one idempotency key for double click and retry', async () => {
    let rejectFirst!: (reason?: unknown) => void;
    vi.mocked(checkoutProduct)
      .mockImplementationOnce(
        () =>
          new Promise((_, reject) => {
            rejectFirst = reject;
          })
      )
      .mockResolvedValueOnce(completedOrder);
    render(<BuyButton product={product} />);

    fireEvent.click(screen.getByRole('button', { name: 'Mua bằng số dư ví' }));
    const confirm = await screen.findByRole('button', { name: 'Xác nhận mua' });
    fireEvent.click(confirm);
    fireEvent.click(confirm);
    expect(checkoutProduct).toHaveBeenCalledTimes(1);

    rejectFirst(new Error('network unavailable'));
    expect(await screen.findByRole('alert')).toHaveTextContent('Không thể hoàn tất thanh toán');
    fireEvent.click(screen.getByRole('button', { name: 'Thử lại thanh toán' }));
    await waitFor(() => expect(checkoutProduct).toHaveBeenCalledTimes(2));

    const firstKey = vi.mocked(checkoutProduct).mock.calls[0]?.[2];
    const retryKey = vi.mocked(checkoutProduct).mock.calls[1]?.[2];
    expect(firstKey).toMatch(/^[A-Za-z0-9_-]{8,100}$/);
    expect(retryKey).toBe(firstKey);
  });

  it('keeps the pending idempotency key across a remount after an ambiguous failure', async () => {
    vi.mocked(checkoutProduct).mockRejectedValueOnce(new Error('response lost')).mockResolvedValueOnce(completedOrder);
    const first = render(<BuyButton product={product} />);

    fireEvent.click(screen.getByRole('button', { name: 'Mua bằng số dư ví' }));
    fireEvent.click(await screen.findByRole('button', { name: 'Xác nhận mua' }));
    await screen.findByRole('alert');
    const firstKey = vi.mocked(checkoutProduct).mock.calls[0]?.[2];
    first.unmount();

    render(<BuyButton product={product} />);
    fireEvent.click(screen.getByRole('button', { name: 'Mua bằng số dư ví' }));
    fireEvent.click(await screen.findByRole('button', { name: 'Xác nhận mua' }));
    await waitFor(() => expect(checkoutProduct).toHaveBeenCalledTimes(2));
    expect(vi.mocked(checkoutProduct).mock.calls[1]?.[2]).toBe(firstKey);
  });

  it.each([
    ['INSUFFICIENT_FUNDS', 'Số dư không đủ', 'Nạp thêm tiền'],
    ['OUT_OF_STOCK', 'Sản phẩm vừa hết hàng', 'Xem sản phẩm khác'],
  ])('shows an actionable %s checkout error', async (code, message, action) => {
    vi.mocked(checkoutProduct).mockRejectedValueOnce(new ApiError(409, { code }));
    render(<BuyButton product={product} />);

    fireEvent.click(screen.getByRole('button', { name: 'Mua bằng số dư ví' }));
    fireEvent.click(await screen.findByRole('button', { name: 'Xác nhận mua' }));

    expect(await screen.findByRole('alert')).toHaveTextContent(message);
    expect(screen.getByRole('link', { name: action })).toBeVisible();
  });

  it('stops checkout and asks for a refresh when the confirmed price changed', async () => {
    vi.mocked(checkoutProduct).mockRejectedValueOnce(new ApiError(409, { code: 'PRODUCT_PRICE_CHANGED' }));
    render(<BuyButton product={product} />);

    fireEvent.click(screen.getByRole('button', { name: 'Mua bằng số dư ví' }));
    fireEvent.click(await screen.findByRole('button', { name: 'Xác nhận mua' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Giá sản phẩm vừa thay đổi');
    expect(screen.getByRole('link', { name: 'Tải lại giá' })).toHaveAttribute('href', `/product/${product.slug}`);
  });

  it('disables checkout when catalog stock is empty', () => {
    render(<BuyButton product={{ ...product, availableCount: 0 }} />);

    expect(screen.getByRole('button', { name: 'Sản phẩm tạm hết hàng' })).toBeDisabled();
    expect(getCurrentWallet).not.toHaveBeenCalled();
  });

  it('closes with Escape and restores focus to the purchase trigger', async () => {
    render(<BuyButton product={product} />);
    const trigger = screen.getByRole('button', { name: 'Mua bằng số dư ví' });
    fireEvent.click(trigger);
    await screen.findByRole('dialog', { name: 'Xác nhận thanh toán' });

    fireEvent.keyDown(document, { key: 'Escape' });

    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    await waitFor(() => expect(trigger).toHaveFocus());
  });

  it('lets the customer reload a wallet balance after a temporary failure', async () => {
    vi.mocked(getCurrentWallet)
      .mockRejectedValueOnce(new Error('wallet temporarily unavailable'))
      .mockResolvedValueOnce({ walletId: 9, balanceVnd: 125_000, currency: 'VND' });
    render(<BuyButton product={product} />);

    fireEvent.click(screen.getByRole('button', { name: 'Mua bằng số dư ví' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('Không thể tải số dư');
    fireEvent.click(screen.getByRole('button', { name: 'Tải lại số dư' }));

    await waitFor(() => expect(getCurrentWallet).toHaveBeenCalledTimes(2));
    expect(await screen.findByText('125.000 ₫')).toBeVisible();
    expect(screen.getByRole('button', { name: 'Xác nhận mua' })).toBeEnabled();
  });

  it('restores focus to the completed purchase trigger when the success dialog closes', async () => {
    render(<BuyButton product={product} />);

    fireEvent.click(screen.getByRole('button', { name: 'Mua bằng số dư ví' }));
    fireEvent.click(await screen.findByRole('button', { name: 'Xác nhận mua' }));
    await screen.findByRole('status');
    fireEvent.click(screen.getAllByRole('button', { name: 'Đóng' }).at(-1)!);

    const completedTrigger = screen.getByRole('button', { name: 'Đã mua thành công' });
    await waitFor(() => expect(completedTrigger).toHaveFocus());
  });

  it('keeps a guarded close control focusable while checkout is pending', async () => {
    vi.mocked(checkoutProduct).mockImplementation(() => new Promise(() => undefined));
    render(<BuyButton product={product} />);

    fireEvent.click(screen.getByRole('button', { name: 'Mua bằng số dư ví' }));
    fireEvent.click(await screen.findByRole('button', { name: 'Xác nhận mua' }));
    const close = screen.getByRole('button', { name: 'Đóng' });

    await waitFor(() => expect(close).toHaveAttribute('aria-disabled', 'true'));
    expect(close).toBeEnabled();
    expect(close).toHaveFocus();
    fireEvent.keyDown(document, { key: 'Tab' });
    expect(close).toHaveFocus();
  });
});
