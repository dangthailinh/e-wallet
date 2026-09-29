import { act, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { TopUpFlow } from '@/components/payment/top-up-flow';
import { createDeposit, getDeposit, type Deposit } from '@/lib/api/deposits';

vi.mock('@/lib/api/deposits', async (importOriginal) => {
  const original = await importOriginal<typeof import('@/lib/api/deposits')>();
  return { ...original, createDeposit: vi.fn(), getDeposit: vi.fn() };
});

const pendingDeposit: Deposit = {
  id: '1e89fbe4-c243-40f8-8458-125afcd5bde4',
  channel: 'MOMO',
  providerCode: 'MOMO',
  merchantReference: 'NAP_4C846E21',
  amountVnd: 100_000,
  currency: 'VND',
  status: 'PENDING',
  expiresAt: '2099-09-26T10:30:00Z',
  paidAt: null,
  instructions: { redirectUrl: 'https://test-payment.momo.vn/checkout/abc' },
};

describe('top-up flow', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('validates a positive whole VND amount before creating a deposit', async () => {
    render(<TopUpFlow />);

    fireEvent.change(screen.getByLabelText('Số tiền nạp'), { target: { value: '0' } });
    fireEvent.click(screen.getByRole('button', { name: 'Tạo yêu cầu nạp tiền' }));

    expect(await screen.findByText('Nhập số tiền từ 1 ₫ đến 1.000.000.000 ₫.')).toBeInTheDocument();
    expect(createDeposit).not.toHaveBeenCalled();
  });

  it('keeps a redirect payment pending until the backend confirms PAID', async () => {
    vi.mocked(createDeposit).mockResolvedValue(pendingDeposit);
    render(<TopUpFlow />);

    fireEvent.change(screen.getByLabelText('Số tiền nạp'), { target: { value: '100000' } });
    fireEvent.click(screen.getByRole('button', { name: 'Tạo yêu cầu nạp tiền' }));

    expect(await screen.findByRole('heading', { name: 'Đang chờ thanh toán' })).toBeInTheDocument();
    expect(screen.queryByRole('heading', { name: 'Nạp tiền thành công' })).not.toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Mở cổng thanh toán MoMo' })).toHaveAttribute(
      'href',
      pendingDeposit.instructions?.redirectUrl
    );
    expect(createDeposit).toHaveBeenCalledWith('MOMO', 100_000);
  });

  it('polls while pending and reports success only after PAID', async () => {
    vi.useFakeTimers();
    vi.mocked(createDeposit).mockResolvedValue(pendingDeposit);
    vi.mocked(getDeposit)
      .mockResolvedValueOnce({ ...pendingDeposit, instructions: null })
      .mockResolvedValueOnce({
        ...pendingDeposit,
        status: 'PAID',
        paidAt: '2026-09-26T10:05:00Z',
        instructions: null,
      });
    render(<TopUpFlow pollIntervalMs={1_000} />);

    fireEvent.change(screen.getByLabelText('Số tiền nạp'), { target: { value: '100000' } });
    fireEvent.click(screen.getByRole('button', { name: 'Tạo yêu cầu nạp tiền' }));
    await act(async () => Promise.resolve());

    await act(async () => {
      await vi.advanceTimersByTimeAsync(1_000);
    });
    expect(screen.queryByRole('heading', { name: 'Nạp tiền thành công' })).not.toBeInTheDocument();

    await act(async () => {
      await vi.advanceTimersByTimeAsync(1_000);
    });
    expect(screen.getByRole('heading', { name: 'Nạp tiền thành công' })).toBeInTheDocument();
    expect(getDeposit).toHaveBeenCalledTimes(2);

    await act(async () => {
      await vi.advanceTimersByTimeAsync(5_000);
    });
    expect(getDeposit).toHaveBeenCalledTimes(2);
  });

  it('stops polling after local expiry and after unmount', async () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date('2026-09-26T10:00:00Z'));
    const expiring = { ...pendingDeposit, expiresAt: '2026-09-26T10:00:01Z' };
    vi.mocked(createDeposit).mockResolvedValue(expiring);
    vi.mocked(getDeposit).mockResolvedValue({ ...expiring, instructions: null });
    const view = render(<TopUpFlow pollIntervalMs={500} />);

    fireEvent.change(screen.getByLabelText('Số tiền nạp'), { target: { value: '100000' } });
    fireEvent.click(screen.getByRole('button', { name: 'Tạo yêu cầu nạp tiền' }));
    await act(async () => Promise.resolve());

    await act(async () => {
      await vi.advanceTimersByTimeAsync(1_100);
    });
    expect(screen.getByRole('heading', { name: 'Yêu cầu đã hết hạn' })).toBeInTheDocument();
    const callsAtExpiry = vi.mocked(getDeposit).mock.calls.length;

    await act(async () => {
      await vi.advanceTimersByTimeAsync(2_000);
    });
    expect(getDeposit).toHaveBeenCalledTimes(callsAtExpiry);

    view.unmount();
    await act(async () => {
      await vi.runOnlyPendingTimersAsync();
    });
    expect(getDeposit).toHaveBeenCalledTimes(callsAtExpiry);
  });

  it('keeps polling REVIEW because reconciliation can still confirm the payment', async () => {
    vi.useFakeTimers();
    vi.mocked(createDeposit).mockResolvedValue(pendingDeposit);
    vi.mocked(getDeposit)
      .mockResolvedValueOnce({ ...pendingDeposit, status: 'REVIEW', instructions: null })
      .mockResolvedValueOnce({
        ...pendingDeposit,
        status: 'PAID',
        paidAt: '2026-09-26T10:05:00Z',
        instructions: null,
      });
    render(<TopUpFlow pollIntervalMs={1_000} />);

    fireEvent.change(screen.getByLabelText('Số tiền nạp'), { target: { value: '100000' } });
    fireEvent.click(screen.getByRole('button', { name: 'Tạo yêu cầu nạp tiền' }));
    await act(async () => Promise.resolve());

    await act(async () => {
      await vi.advanceTimersByTimeAsync(1_000);
    });
    expect(screen.getByRole('heading', { name: 'Giao dịch đang được kiểm tra' })).toBeInTheDocument();

    await act(async () => {
      await vi.advanceTimersByTimeAsync(1_000);
    });
    expect(screen.getByRole('heading', { name: 'Nạp tiền thành công' })).toBeInTheDocument();
  });

  it('offers only MoMo and submits it without a channel choice', async () => {
    vi.mocked(createDeposit).mockResolvedValue(pendingDeposit);
    render(<TopUpFlow />);

    expect(screen.getByText('Ví MoMo')).toBeInTheDocument();
    expect(screen.queryByText('Chuyển khoản ngân hàng')).not.toBeInTheDocument();
    fireEvent.change(screen.getByLabelText('Số tiền nạp'), { target: { value: '100000' } });
    fireEvent.click(screen.getByRole('button', { name: 'Tạo yêu cầu nạp tiền' }));

    expect(await screen.findByRole('heading', { name: 'Đang chờ thanh toán' })).toBeInTheDocument();
    expect(createDeposit).toHaveBeenCalledWith('MOMO', 100_000);
  });
});
