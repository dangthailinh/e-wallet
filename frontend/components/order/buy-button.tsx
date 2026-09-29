'use client';

import { useRef, useState } from 'react';

import { BuyDialog } from '@/components/order/buy-dialog';
import type { CatalogProductDetail } from '@/lib/contracts/catalog';

export function BuyButton({ product }: { product: CatalogProductDetail }) {
  const [open, setOpen] = useState(false);
  const [purchased, setPurchased] = useState(false);
  const idempotencyKey = useRef<string | null>(null);
  const triggerRef = useRef<HTMLButtonElement>(null);
  const inStock = product.availableCount > 0;
  const storageKey = `storefront.checkout.pending.${product.id}`;

  function getIdempotencyKey(): string {
    if (idempotencyKey.current) return idempotencyKey.current;
    try {
      const stored = sessionStorage.getItem(storageKey);
      if (stored && /^[A-Za-z0-9_-]{8,100}$/.test(stored)) {
        idempotencyKey.current = stored;
        return stored;
      }
    } catch {
      // The in-memory key still protects double submits when storage is unavailable.
    }
    idempotencyKey.current = crypto.randomUUID();
    try {
      sessionStorage.setItem(storageKey, idempotencyKey.current);
    } catch {
      // Some privacy modes disable session storage.
    }
    return idempotencyKey.current;
  }

  function clearIdempotencyKey() {
    idempotencyKey.current = null;
    try {
      sessionStorage.removeItem(storageKey);
    } catch {
      // Nothing else is required when storage is unavailable.
    }
  }

  function completePurchase() {
    clearIdempotencyKey();
    setPurchased(true);
  }

  function closeDialog() {
    setOpen(false);
    requestAnimationFrame(() => triggerRef.current?.focus());
  }

  return (
    <>
      <button
        ref={triggerRef}
        className="button button-primary product-buy-button"
        type="button"
        disabled={!inStock}
        aria-disabled={purchased}
        onClick={() => !purchased && setOpen(true)}
        aria-describedby="purchase-note"
      >
        {purchased ? 'Đã mua thành công' : inStock ? 'Mua bằng số dư ví' : 'Sản phẩm tạm hết hàng'}
      </button>
      <p id="purchase-note" className="purchase-note">
        Thanh toán từ số dư ví. Sản phẩm chỉ được bàn giao sau khi giao dịch hoàn tất.
      </p>
      {open && (
        <BuyDialog
          product={product}
          getIdempotencyKey={getIdempotencyKey}
          onCompleted={completePurchase}
          onTerminalFailure={clearIdempotencyKey}
          onClose={closeDialog}
        />
      )}
    </>
  );
}
