'use client';

import Link from 'next/link';
import { createPortal } from 'react-dom';
import { useEffect, useRef, useState } from 'react';

import { ApiError } from '@/lib/api/client';
import { getCurrentWallet, getWalletEntries, type WalletEntriesPage, type WalletView } from '@/lib/api/wallet';

type WalletDrawerProps = { onSessionExpired: () => void };

const formatVnd = (amount: number) => `${new Intl.NumberFormat('vi-VN').format(amount)} ₫`;
const compactVnd = (amount: number) => {
  if (amount >= 1_000_000_000)
    return `${new Intl.NumberFormat('vi-VN', { maximumFractionDigits: 1 }).format(amount / 1_000_000_000)} tỷ ₫`;
  if (amount >= 1_000_000)
    return `${new Intl.NumberFormat('vi-VN', { maximumFractionDigits: 1 }).format(amount / 1_000_000)} tr ₫`;
  if (amount >= 1_000)
    return `${new Intl.NumberFormat('vi-VN', { maximumFractionDigits: 1 }).format(amount / 1_000)}k ₫`;
  return formatVnd(amount);
};

export function WalletDrawer({ onSessionExpired }: WalletDrawerProps) {
  const [wallet, setWallet] = useState<WalletView | null>(null);
  const [walletLoading, setWalletLoading] = useState(true);
  const [walletError, setWalletError] = useState(false);
  const [entries, setEntries] = useState<WalletEntriesPage | null>(null);
  const [entriesLoading, setEntriesLoading] = useState(false);
  const [entriesError, setEntriesError] = useState(false);
  const [open, setOpen] = useState(false);
  const [expired, setExpired] = useState(false);
  const [walletRetry, setWalletRetry] = useState(0);
  const [entriesRetry, setEntriesRetry] = useState(0);
  const triggerRef = useRef<HTMLButtonElement>(null);
  const panelRef = useRef<HTMLElement>(null);
  const closeRef = useRef<HTMLButtonElement>(null);

  useEffect(() => {
    let active = true;
    getCurrentWallet()
      .then((value) => {
        if (!active) return;
        setWallet(value);
        setWalletError(false);
      })
      .catch((error: unknown) => {
        if (!active) return;
        if (error instanceof ApiError && error.status === 401) {
          setExpired(true);
          setOpen(false);
          onSessionExpired();
        } else {
          setWalletError(true);
        }
      })
      .finally(() => {
        if (active) setWalletLoading(false);
      });
    return () => {
      active = false;
    };
  }, [walletRetry, onSessionExpired]);

  useEffect(() => {
    if (!open) return;
    let active = true;
    getWalletEntries(0, 5)
      .then((value) => {
        if (!active) return;
        setEntries(value);
        setEntriesError(false);
      })
      .catch((error: unknown) => {
        if (!active) return;
        if (error instanceof ApiError && error.status === 401) {
          setExpired(true);
          setOpen(false);
          onSessionExpired();
        } else {
          setEntriesError(true);
        }
      })
      .finally(() => {
        if (active) setEntriesLoading(false);
      });
    return () => {
      active = false;
    };
  }, [open, entriesRetry, onSessionExpired]);

  useEffect(() => {
    if (!open) return;
    const priorOverflow = document.body.style.overflow;
    const trigger = triggerRef.current;
    document.body.style.overflow = 'hidden';
    closeRef.current?.focus();

    function onKeyDown(event: KeyboardEvent) {
      if (event.key === 'Escape') {
        setOpen(false);
        return;
      }
      if (event.key !== 'Tab') return;
      const focusable = panelRef.current?.querySelectorAll<HTMLElement>('a[href], button:not([disabled])');
      if (!focusable?.length) return;
      const first = focusable[0]!;
      const last = focusable[focusable.length - 1]!;
      if (event.shiftKey && document.activeElement === first) {
        event.preventDefault();
        last.focus();
      } else if (!event.shiftKey && document.activeElement === last) {
        event.preventDefault();
        first.focus();
      }
    }
    document.addEventListener('keydown', onKeyDown);
    return () => {
      document.body.style.overflow = priorOverflow;
      document.removeEventListener('keydown', onKeyDown);
      trigger?.focus();
    };
  }, [open]);

  if (expired) return null;

  const balanceLabel = walletLoading
    ? 'Ví: đang tải'
    : walletError || !wallet
      ? 'Ví: chưa khả dụng'
      : `Ví: ${formatVnd(wallet.balanceVnd)}`;

  function openDrawer() {
    setEntriesLoading(true);
    setEntriesError(false);
    setEntries(null);
    setOpen(true);
  }

  return (
    <>
      <button
        ref={triggerRef}
        type="button"
        className="wallet-trigger"
        onClick={openDrawer}
        aria-label={balanceLabel}
        aria-haspopup="dialog"
        aria-expanded={open}
      >
        <svg aria-hidden="true" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8">
          <rect x="2.5" y="5" width="19" height="15" rx="3" />
          <path d="M17 10h4.5v5H17a2.5 2.5 0 0 1 0-5ZM5 5V3.5h13" />
        </svg>
        <span className="wallet-trigger-full" aria-hidden="true">
          {balanceLabel}
        </span>
        <span className="wallet-trigger-compact" aria-hidden="true">
          {walletLoading ? 'Đang tải' : walletError || !wallet ? 'Ví' : compactVnd(wallet.balanceVnd)}
        </span>
      </button>
      {open &&
        typeof document !== 'undefined' &&
        createPortal(
          <div className="wallet-overlay">
            <button type="button" className="wallet-backdrop" aria-label="Đóng ví" onClick={() => setOpen(false)} />
            <aside
              ref={panelRef}
              className="wallet-panel"
              role="dialog"
              aria-modal="true"
              aria-labelledby="wallet-title"
            >
              <div className="wallet-panel-header">
                <div>
                  <p className="eyebrow">Tài khoản của bạn</p>
                  <h2 id="wallet-title">Ví của tôi</h2>
                </div>
                <button
                  ref={closeRef}
                  type="button"
                  className="wallet-close"
                  aria-label="Đóng ví"
                  onClick={() => setOpen(false)}
                >
                  ×
                </button>
              </div>

              <div className="wallet-balance-card">
                <span>Số dư khả dụng</span>
                {walletLoading && <strong role="status">Đang tải số dư…</strong>}
                {!walletLoading && walletError && <strong>Không thể tải số dư.</strong>}
                {!walletLoading && !walletError && wallet && <strong>{formatVnd(wallet.balanceVnd)}</strong>}
                {walletError && (
                  <button
                    type="button"
                    className="wallet-retry"
                    onClick={() => {
                      setWalletLoading(true);
                      setWalletRetry((value) => value + 1);
                    }}
                  >
                    Thử tải lại
                  </button>
                )}
              </div>

              <Link href="/top-up" className="button button-primary wallet-top-up" onClick={() => setOpen(false)}>
                Nạp tiền vào ví
              </Link>
              <p className="wallet-payment-note">
                Chỉ cộng tiền sau khi giao dịch được xác nhận. Không có nạp tiền giả lập.
              </p>

              <div className="wallet-entries-heading">
                <h3>Giao dịch gần đây</h3>
                <span>5 giao dịch mới nhất</span>
              </div>
              {entriesLoading && (
                <p className="wallet-state" role="status">
                  Đang tải giao dịch…
                </p>
              )}
              {!entriesLoading && entriesError && (
                <div className="wallet-state" role="alert">
                  <p>Không thể tải lịch sử giao dịch.</p>
                  <button
                    type="button"
                    className="wallet-retry"
                    onClick={() => {
                      setEntriesLoading(true);
                      setEntriesRetry((value) => value + 1);
                    }}
                  >
                    Thử lại
                  </button>
                </div>
              )}
              {!entriesLoading && !entriesError && entries?.items.length === 0 && (
                <p className="wallet-state">Chưa có giao dịch nào.</p>
              )}
              {!entriesLoading && !entriesError && entries && entries.items.length > 0 && (
                <ul className="wallet-entries">
                  {entries.items.map((entry) => (
                    <li key={entry.id}>
                      <div>
                        <strong>{entry.businessLabel}</strong>
                        <time dateTime={entry.createdAt}>
                          {new Intl.DateTimeFormat('vi-VN', { dateStyle: 'short', timeStyle: 'short' }).format(
                            new Date(entry.createdAt)
                          )}
                        </time>
                      </div>
                      <span className={entry.direction === 'CREDIT' ? 'wallet-credit' : 'wallet-debit'}>
                        {entry.direction === 'CREDIT' ? '+' : '−'}
                        {formatVnd(entry.amountVnd)}
                      </span>
                    </li>
                  ))}
                </ul>
              )}
            </aside>
          </div>,
          document.body
        )}
    </>
  );
}
