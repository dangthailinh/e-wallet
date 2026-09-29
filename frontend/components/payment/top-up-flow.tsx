'use client';

import { FormEvent, useCallback, useState } from 'react';

import { DepositStatusView } from '@/components/payment/deposit-status';
import { ApiError } from '@/lib/api/client';
import { createDeposit, type Deposit } from '@/lib/api/deposits';

type TopUpFlowProps = { pollIntervalMs?: number };

const MAXIMUM_VND = 1_000_000_000;
const suggestedAmounts = [50_000, 100_000, 200_000, 500_000];
const formatVnd = (amount: number) => `${new Intl.NumberFormat('vi-VN').format(amount)} ₫`;

export function TopUpFlow({ pollIntervalMs = 3_000 }: TopUpFlowProps) {
  const [amount, setAmount] = useState('');
  const [deposit, setDeposit] = useState<Deposit | null>(null);
  const [amountError, setAmountError] = useState('');
  const [requestError, setRequestError] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const updateDeposit = useCallback((value: Deposit) => setDeposit(value), []);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const amountVnd = Number(amount);
    if (!Number.isSafeInteger(amountVnd) || amountVnd <= 0 || amountVnd > MAXIMUM_VND) {
      setAmountError('Nhập số tiền từ 1 ₫ đến 1.000.000.000 ₫.');
      return;
    }

    setAmountError('');
    setRequestError('');
    setSubmitting(true);
    try {
      setDeposit(await createDeposit('MOMO', amountVnd));
    } catch (error: unknown) {
      if (error instanceof ApiError && error.status === 401) {
        setRequestError('Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.');
      } else if (error instanceof ApiError && error.status === 503) {
        setRequestError('MoMo đang tạm thời chưa sẵn sàng. Vui lòng thử lại sau.');
      } else {
        setRequestError('Không thể tạo yêu cầu nạp tiền. Vui lòng thử lại.');
      }
    } finally {
      setSubmitting(false);
    }
  }

  if (deposit) {
    return (
      <DepositStatusView
        deposit={deposit}
        onChange={updateDeposit}
        onReset={() => {
          setDeposit(null);
          setRequestError('');
        }}
        pollIntervalMs={pollIntervalMs}
      />
    );
  }

  return (
    <form className="top-up-form" onSubmit={submit} noValidate>
      <section className="top-up-method" aria-labelledby="top-up-method-title">
        <span className="top-up-method-mark" aria-hidden="true">M</span>
        <span>
          <strong id="top-up-method-title">Ví MoMo</strong>
          <small>Mở cổng MoMo để xác nhận thanh toán</small>
        </span>
      </section>

      <div className="top-up-field">
        <label htmlFor="top-up-amount">Số tiền nạp</label>
        <div className="amount-input">
          <input
            id="top-up-amount"
            name="amountVnd"
            type="text"
            inputMode="numeric"
            autoComplete="off"
            value={amount}
            aria-invalid={Boolean(amountError)}
            aria-describedby={amountError ? 'top-up-amount-error' : 'top-up-amount-hint'}
            onChange={(event) => {
              setAmount(event.target.value.replace(/[^0-9]/g, ''));
              setAmountError('');
            }}
          />
          <span>VND</span>
        </div>
        {amountError ? (
          <p id="top-up-amount-error" className="auth-field-error" role="alert">
            {amountError}
          </p>
        ) : (
          <p id="top-up-amount-hint" className="auth-field-hint">
            Số dư chỉ tăng sau khi webhook thanh toán được xác minh.
          </p>
        )}
        <div className="amount-suggestions" aria-label="Chọn nhanh số tiền">
          {suggestedAmounts.map((value) => (
            <button key={value} type="button" onClick={() => setAmount(String(value))}>
              {formatVnd(value)}
            </button>
          ))}
        </div>
      </div>

      {requestError && (
        <p className="auth-request-error" role="alert">
          {requestError}
        </p>
      )}
      <button className="button button-primary top-up-submit" type="submit" disabled={submitting}>
        {submitting ? 'Đang tạo yêu cầu…' : 'Tạo yêu cầu nạp tiền'}
      </button>
      <p className="top-up-legal">Không gửi mã/API key qua email. Mọi trạng thái thanh toán được đối chiếu tại máy chủ.</p>
    </form>
  );
}
