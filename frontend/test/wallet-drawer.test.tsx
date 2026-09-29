import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { WalletDrawer } from '@/components/wallet/wallet-drawer';
import { ApiError } from '@/lib/api/client';
import { getCurrentWallet, getWalletEntries } from '@/lib/api/wallet';

vi.mock('@/lib/api/wallet', () => ({
  getCurrentWallet: vi.fn(),
  getWalletEntries: vi.fn(),
}));

const wallet = { walletId: 9, balanceVnd: 125_000, currency: 'VND' as const };
const entries = {
  items: [
    {
      id: '835ca32c-196d-4f6e-9235-03f36bca7caf',
      direction: 'CREDIT' as const,
      type: 'DEPOSIT' as const,
      amountVnd: 125_000,
      balanceAfterVnd: 125_000,
      businessLabel: 'Nạp tiền',
      createdAt: '2026-09-18T01:00:00Z',
    },
  ],
  page: 0,
  size: 5,
  totalItems: 1,
  totalPages: 1,
};

describe('wallet-drawer', () => {
  beforeEach(() => {
    vi.mocked(getCurrentWallet).mockReset();
    vi.mocked(getWalletEntries).mockReset();
    vi.mocked(getCurrentWallet).mockResolvedValue(wallet);
    vi.mocked(getWalletEntries).mockResolvedValue(entries);
  });

  it('shows real balance and recent ledger only after opening', async () => {
    render(<WalletDrawer onSessionExpired={vi.fn()} />);

    expect(await screen.findByRole('button', { name: /Ví: 125\.000 ₫/ })).toBeVisible();
    expect(screen.getByText('125k ₫')).toBeInTheDocument();
    expect(getWalletEntries).not.toHaveBeenCalled();
    fireEvent.click(screen.getByRole('button', { name: /Ví: 125\.000 ₫/ }));

    expect(await screen.findByRole('dialog', { name: 'Ví của tôi' })).toBeVisible();
    expect(await screen.findByText('Nạp tiền')).toBeVisible();
    expect(screen.getByText('+125.000 ₫')).toBeVisible();
    expect(getWalletEntries).toHaveBeenCalledWith(0, 5);
    expect(screen.queryByText(/phí vận chuyển|số lượng|thuế/i)).not.toBeInTheDocument();

    fireEvent.keyDown(document, { key: 'Escape' });
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });

  it('shows an empty state and handles a wallet loading error without fake zero balance', async () => {
    vi.mocked(getCurrentWallet).mockRejectedValueOnce(new Error('offline'));
    vi.mocked(getWalletEntries).mockResolvedValueOnce({ ...entries, items: [], totalItems: 0, totalPages: 0 });
    render(<WalletDrawer onSessionExpired={vi.fn()} />);

    expect(await screen.findByRole('button', { name: 'Ví: chưa khả dụng' })).toBeVisible();
    fireEvent.click(screen.getByRole('button', { name: 'Ví: chưa khả dụng' }));
    expect(await screen.findByText('Chưa có giao dịch nào.')).toBeVisible();
    expect(screen.queryByText('0 ₫')).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Thử tải lại' }));
    expect(await screen.findByRole('button', { name: /Ví: 125\.000 ₫/ })).toBeVisible();
  });

  it('offers a retry when recent entries cannot be loaded', async () => {
    vi.mocked(getWalletEntries).mockRejectedValueOnce(new Error('offline'));
    render(<WalletDrawer onSessionExpired={vi.fn()} />);

    fireEvent.click(await screen.findByRole('button', { name: /Ví: 125\.000 ₫/ }));
    expect(await screen.findByRole('alert')).toHaveTextContent('Không thể tải lịch sử giao dịch.');
    fireEvent.click(screen.getByRole('button', { name: 'Thử lại' }));
    expect(await screen.findByText('Nạp tiền')).toBeVisible();
  });

  it('closes private data when the wallet request reports an expired session', async () => {
    const onSessionExpired = vi.fn();
    vi.mocked(getWalletEntries).mockRejectedValueOnce(new ApiError(401));
    render(<WalletDrawer onSessionExpired={onSessionExpired} />);

    fireEvent.click(await screen.findByRole('button', { name: /Ví: 125\.000 ₫/ }));
    await waitFor(() => expect(onSessionExpired).toHaveBeenCalledOnce());
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    expect(screen.queryByText('125.000 ₫')).not.toBeInTheDocument();
  });

  it('does not expose the balance after unmounting for logout', async () => {
    const view = render(<WalletDrawer onSessionExpired={vi.fn()} />);
    await screen.findByRole('button', { name: /Ví: 125\.000 ₫/ });
    view.unmount();
    expect(screen.queryByText('125.000 ₫')).not.toBeInTheDocument();
  });
});
