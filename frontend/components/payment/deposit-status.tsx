'use client';

import Link from 'next/link';
import { useEffect, useState } from 'react';

import { ApiError } from '@/lib/api/client';
import { getDeposit, type Deposit } from '@/lib/api/deposits';

type DepositStatusViewProps = {
  deposit: Deposit;
  onChange: (deposit: Deposit) => void;
  onReset: () => void;
  pollIntervalMs: number;
};

const terminalStatuses = new Set<Deposit['status']>(['PAID', 'FAILED', 'EXPIRED']);
const formatVnd = (amount: number) => `${new Intl.NumberFormat('vi-VN').format(amount)} ₫`;

export function DepositStatusView({ deposit, onChange, onReset, pollIntervalMs }: DepositStatusViewProps) {
  const [pollError, setPollError] = useState(false);
  const [retryTick, setRetryTick] = useState(0);

  useEffect(() => {
    if (terminalStatuses.has(deposit.status)) return;
    let active = true;
    const remainingMs = Date.parse(deposit.expiresAt) - Date.now();
    if (remainingMs <= 0) {
      onChange({ ...deposit, status: 'EXPIRED' });
      return;
    }

    const timer = window.setTimeout(async () => {
      if (Date.now() >= Date.parse(deposit.expiresAt)) {
        if (active) onChange({ ...deposit, status: 'EXPIRED' });
        return;
      }
      try {
        const latest = await getDeposit(deposit.id);
        if (!active) return;
        setPollError(false);
        onChange({ ...latest, instructions: deposit.instructions });
      } catch (error: unknown) {
        if (!active) return;
        setPollError(true);
        if (!(error instanceof ApiError && error.status === 401)) setRetryTick((value) => value + 1);
      }
    }, Math.min(pollIntervalMs, remainingMs));

    return () => {
      active = false;
      window.clearTimeout(timer);
    };
  }, [deposit, onChange, pollIntervalMs, retryTick]);

  if (deposit.status === 'PAID') {
    return (
      <StatusPanel eyebrow="Đã xác nhận" title="Nạp tiền thành công" tone="success">
        <p>{formatVnd(deposit.amountVnd)} đã được cộng vào ví. Anh có thể quay lại cửa hàng để mua sản phẩm.</p>
        <div className="top-up-actions">
          <Link className="button button-primary" href="/">
            Mua hàng
          </Link>
          <button className="button button-secondary" type="button" onClick={onReset}>
            Nạp thêm
          </button>
        </div>
      </StatusPanel>
    );
  }

  if (deposit.status === 'EXPIRED') {
    return (
      <StatusPanel eyebrow="Đã dừng kiểm tra" title="Yêu cầu đã hết hạn" tone="danger">
        <p>Không chuyển khoản theo yêu cầu cũ. Hãy tạo yêu cầu mới để nhận nội dung và mã thanh toán còn hiệu lực.</p>
        <button className="button button-primary" type="button" onClick={onReset}>
          Tạo yêu cầu mới
        </button>
      </StatusPanel>
    );
  }

  if (deposit.status === 'FAILED') {
    return (
      <StatusPanel eyebrow="Không thành công" title="Thanh toán chưa hoàn tất" tone="danger">
        <p>Cổng thanh toán báo giao dịch thất bại. Số dư ví chưa được thay đổi.</p>
        <button className="button button-primary" type="button" onClick={onReset}>
          Thử lại
        </button>
      </StatusPanel>
    );
  }

  if (deposit.status === 'REVIEW') {
    return (
      <StatusPanel eyebrow="Cần đối soát" title="Giao dịch đang được kiểm tra" tone="warning">
        <p>Hệ thống đã nhận giao dịch nhưng cần đối soát thêm. Không thanh toán lại yêu cầu này.</p>
        <p className="deposit-poll-state" role="status">
          {pollError ? 'Tạm mất kết nối, hệ thống sẽ tự kiểm tra lại…' : 'Đang tự động kiểm tra kết quả đối soát…'}
        </p>
      </StatusPanel>
    );
  }

  return (
    <StatusPanel eyebrow="Chưa cộng số dư" title="Đang chờ thanh toán" tone="pending">
      <div className="deposit-summary" aria-label="Thông tin yêu cầu nạp tiền">
        <div>
          <span>Số tiền</span>
          <strong>{formatVnd(deposit.amountVnd)}</strong>
        </div>
        <div>
          <span>Nội dung chuyển khoản</span>
          <strong>{deposit.merchantReference}</strong>
        </div>
      </div>

      {deposit.instructions?.redirectUrl && (
        <a
          className="button button-primary top-up-provider-link"
          href={deposit.instructions.redirectUrl}
          target="_blank"
          rel="noopener noreferrer"
        >
          Mở cổng thanh toán MoMo
        </a>
      )}
      {deposit.instructions?.qrPayload && (
        <div className="qr-payload" aria-label="Dữ liệu VietQR">
          <span>Dữ liệu QR bảo mật từ cổng thanh toán</span>
          <code>{deposit.instructions.qrPayload}</code>
        </div>
      )}

      <div className="payment-warning" role="note">
        <strong>Giữ nguyên số tiền và nội dung.</strong>
        <span>Trang sẽ tự cập nhật sau khi MoMo xác nhận. Không cần thanh toán lần hai.</span>
      </div>
      <p className="deposit-poll-state" role="status">
        {pollError ? 'Tạm mất kết nối, hệ thống sẽ tự kiểm tra lại…' : 'Đang tự động kiểm tra trạng thái…'}
      </p>
    </StatusPanel>
  );
}

function StatusPanel({
  eyebrow,
  title,
  tone,
  children,
}: {
  eyebrow: string;
  title: string;
  tone: 'success' | 'danger' | 'warning' | 'pending';
  children: React.ReactNode;
}) {
  return (
    <section className={`deposit-status deposit-status-${tone}`} aria-live="polite">
      <p className="eyebrow">{eyebrow}</p>
      <h2>{title}</h2>
      {children}
    </section>
  );
}
