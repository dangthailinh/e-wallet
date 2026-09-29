'use client';

import Link from 'next/link';
import { createPortal } from 'react-dom';
import { useEffect, useRef, useState } from 'react';

import { checkoutProduct, type CheckoutOrder } from '@/lib/api/orders';
import { ApiError } from '@/lib/api/errors';
import { getCurrentWallet, type WalletView } from '@/lib/api/wallet';
import type { CatalogProductDetail } from '@/lib/contracts/catalog';

type CheckoutError = 'auth' | 'funds' | 'stock' | 'price' | 'generic';
type BuyDialogProps = {
  product: CatalogProductDetail;
  getIdempotencyKey: () => string;
  onCompleted: () => void;
  onTerminalFailure: () => void;
  onClose: () => void;
};

const formatVnd = (amount: number) => `${new Intl.NumberFormat('vi-VN').format(amount)} ₫`;

export function BuyDialog({ product, getIdempotencyKey, onCompleted, onTerminalFailure, onClose }: BuyDialogProps) {
  const [wallet, setWallet] = useState<WalletView | null>(null);
  const [walletError, setWalletError] = useState<CheckoutError | null>(null);
  const [walletAttempt, setWalletAttempt] = useState(0);
  const [pending, setPending] = useState(false);
  const [checkoutError, setCheckoutError] = useState<CheckoutError | null>(null);
  const [order, setOrder] = useState<CheckoutOrder | null>(null);
  const panelRef = useRef<HTMLElement>(null);
  const closeRef = useRef<HTMLButtonElement>(null);

  useEffect(() => {
    let active = true;
    getCurrentWallet()
      .then((value) => active && setWallet(value))
      .catch((error: unknown) => active && setWalletError(classifyError(error)));
    return () => {
      active = false;
    };
  }, [walletAttempt]);

  useEffect(() => {
    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    closeRef.current?.focus();
    function onKeyDown(event: KeyboardEvent) {
      if (event.key === 'Escape' && !pending) onClose();
      if (event.key !== 'Tab') return;
      const elements = panelRef.current?.querySelectorAll<HTMLElement>('a[href], button:not([disabled])');
      if (!elements?.length) return;
      const first = elements[0]!;
      const last = elements[elements.length - 1]!;
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
      document.body.style.overflow = previousOverflow;
      document.removeEventListener('keydown', onKeyDown);
    };
  }, [onClose, pending]);

  async function submit() {
    if (pending || !wallet) return;
    setPending(true);
    setCheckoutError(null);
    try {
      const result = await checkoutProduct(product.id, product.priceVnd, getIdempotencyKey());
      setOrder(result);
      onCompleted();
    } catch (error) {
      const kind = classifyError(error);
      setCheckoutError(kind);
      if (kind !== 'generic') onTerminalFailure();
    } finally {
      setPending(false);
    }
  }

  function retryWallet() {
    setWallet(null);
    setWalletError(null);
    setWalletAttempt((attempt) => attempt + 1);
  }

  const expectedBalance = wallet ? wallet.balanceVnd - product.priceVnd : null;
  const content = (
    <div className="checkout-overlay">
      <button
        className="checkout-backdrop"
        type="button"
        aria-label="Đóng thanh toán"
        onClick={onClose}
        disabled={pending}
      />
      <section
        ref={panelRef}
        className="checkout-dialog"
        role="dialog"
        aria-modal="true"
        aria-labelledby="checkout-title"
      >
        <header className="checkout-header">
          <div>
            <p className="eyebrow">Thanh toán bằng ví</p>
            <h2 id="checkout-title">Xác nhận thanh toán</h2>
          </div>
          <button
            ref={closeRef}
            type="button"
            className="checkout-close"
            aria-label="Đóng"
            aria-disabled={pending}
            onClick={() => !pending && onClose()}
          >
            <svg aria-hidden="true" viewBox="0 0 24 24">
              <path d="m6 6 12 12M18 6 6 18" />
            </svg>
          </button>
        </header>

        {order ? (
          <div className="checkout-success" role="status">
            <strong>Thanh toán thành công</strong>
            <p>Sản phẩm đã được lưu vào lịch sử đơn hàng của anh.</p>
            <dl>
              <div>
                <dt>Số dư mới</dt>
                <dd>{formatVnd(order.walletBalanceVnd)}</dd>
              </div>
            </dl>
          </div>
        ) : (
          <>
            <div className="checkout-product">
              <span>{product.category.name}</span>
              <strong>{product.name}</strong>
            </div>
            <dl className="checkout-summary">
              <div>
                <dt>Giá sản phẩm</dt>
                <dd>{formatVnd(product.priceVnd)}</dd>
              </div>
              <div>
                <dt>Số dư hiện tại</dt>
                <dd>{wallet ? formatVnd(wallet.balanceVnd) : 'Đang tải…'}</dd>
              </div>
              <div>
                <dt>Số dư sau mua</dt>
                <dd>
                  {expectedBalance === null
                    ? 'Đang tính…'
                    : expectedBalance >= 0
                      ? formatVnd(expectedBalance)
                      : `Không đủ (thiếu ${formatVnd(-expectedBalance)})`}
                </dd>
              </div>
            </dl>
            {(walletError || checkoutError) && (
              <CheckoutErrorView
                kind={checkoutError ?? walletError!}
                walletLoad={Boolean(walletError && !checkoutError)}
                onRetry={walletError === 'generic' && !checkoutError ? retryWallet : undefined}
                productSlug={product.slug}
              />
            )}
          </>
        )}

        <footer className="checkout-actions">
          <button className="button button-secondary" type="button" onClick={onClose} disabled={pending}>
            {order ? 'Đóng' : 'Hủy'}
          </button>
          {!order && !walletError && !['auth', 'funds', 'stock', 'price'].includes(checkoutError ?? '') && (
            <button className="button button-primary" type="button" onClick={submit} disabled={!wallet || pending}>
              {pending ? 'Đang thanh toán…' : checkoutError === 'generic' ? 'Thử lại thanh toán' : 'Xác nhận mua'}
            </button>
          )}
        </footer>
      </section>
    </div>
  );
  return typeof document === 'undefined' ? null : createPortal(content, document.body);
}

function CheckoutErrorView({
  kind,
  walletLoad = false,
  onRetry,
  productSlug,
}: {
  kind: CheckoutError;
  walletLoad?: boolean;
  onRetry?: () => void;
  productSlug: string;
}) {
  const copy = walletLoad
    ? 'Không thể tải số dư ví.'
    : kind === 'funds'
      ? 'Số dư không đủ để mua sản phẩm này.'
      : kind === 'stock'
        ? 'Sản phẩm vừa hết hàng.'
        : kind === 'price'
          ? 'Giá sản phẩm vừa thay đổi. Vui lòng tải lại trước khi thanh toán.'
          : kind === 'auth'
            ? 'Phiên đăng nhập đã hết hạn.'
            : 'Không thể hoàn tất thanh toán. Anh có thể thử lại với cùng giao dịch.';
  return (
    <div className="checkout-error" role="alert">
      <p>{copy}</p>
      {onRetry && (
        <button type="button" onClick={onRetry}>
          Tải lại số dư
        </button>
      )}
      {kind === 'funds' && <Link href="/top-up">Nạp thêm tiền</Link>}
      {kind === 'stock' && <Link href="/search">Xem sản phẩm khác</Link>}
      {kind === 'price' && <Link href={`/product/${productSlug}`}>Tải lại giá</Link>}
      {kind === 'auth' && <Link href="/login">Đăng nhập lại</Link>}
    </div>
  );
}

function classifyError(error: unknown): CheckoutError {
  if (error instanceof ApiError) {
    if (error.status === 401) return 'auth';
    if (error.code === 'INSUFFICIENT_FUNDS') return 'funds';
    if (error.code === 'OUT_OF_STOCK') return 'stock';
    if (error.code === 'PRODUCT_PRICE_CHANGED') return 'price';
  }
  return 'generic';
}
