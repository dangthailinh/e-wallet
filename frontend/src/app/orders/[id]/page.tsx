'use client';

import Link from 'next/link';
import { useParams } from 'next/navigation';
import { useEffect, useState } from 'react';

import { SecretReveal } from '@/components/order/secret-reveal';
import { ApiError } from '@/lib/api/errors';
import { getOrder, type OrderDetail } from '@/lib/api/orders';

const money = new Intl.NumberFormat('vi-VN');
const timestamp = new Intl.DateTimeFormat('vi-VN', { dateStyle: 'long', timeStyle: 'short' });

export default function OrderDetailPage() {
  const params = useParams<{ id: string }>();
  const orderId = params?.id ?? '';
  const [attempt, setAttempt] = useState(0);
  const [order, setOrder] = useState<OrderDetail>();
  const [error, setError] = useState<'auth' | 'missing' | 'generic'>();

  useEffect(() => {
    let active = true;
    setOrder(undefined);
    setError(undefined);
    getOrder(orderId)
      .then((value) => active && setOrder(value))
      .catch((reason: unknown) => {
        if (!active) return;
        if (reason instanceof ApiError && reason.status === 401) setError('auth');
        else if (reason instanceof ApiError && reason.status === 404) setError('missing');
        else setError('generic');
      });
    return () => {
      active = false;
    };
  }, [attempt, orderId]);

  if (!order && !error) {
    return (
      <main className="page-shell py-12" role="status">
        Đang tải chi tiết đơn hàng…
      </main>
    );
  }

  if (error) {
    return (
      <main className="state-page">
        <p className="eyebrow">Đơn hàng</p>
        <h1>
          {error === 'missing'
            ? 'Không tìm thấy đơn hàng'
            : error === 'auth'
              ? 'Cần đăng nhập'
              : 'Chưa tải được đơn hàng'}
        </h1>
        <p role="alert">
          {error === 'missing'
            ? 'Đơn hàng không tồn tại hoặc không thuộc tài khoản của anh.'
            : error === 'auth'
              ? 'Phiên đăng nhập đã hết hạn.'
              : 'Kết nối đang gián đoạn. Dữ liệu đơn hàng không bị thay đổi.'}
        </p>
        <div className="state-actions">
          {error === 'auth' ? (
            <Link className="button button-primary" href="/login">
              Đăng nhập lại
            </Link>
          ) : error === 'generic' ? (
            <button className="button button-primary" type="button" onClick={() => setAttempt((value) => value + 1)}>
              Tải lại
            </button>
          ) : null}
          <Link className="button button-secondary" href="/orders">
            Về lịch sử đơn hàng
          </Link>
        </div>
      </main>
    );
  }

  if (!order) return null;

  return (
    <main className="page-shell py-10 sm:py-14" aria-labelledby="order-title">
      <nav className="mb-6 text-sm text-[var(--muted)]" aria-label="Đường dẫn">
        <Link className="hover:text-[var(--foreground)]" href="/orders">
          Đơn hàng
        </Link>{' '}
        <span aria-hidden="true">/</span> <span aria-current="page">#{order.orderId.slice(0, 8).toUpperCase()}</span>
      </nav>

      <header className="mb-7 grid gap-5 border-b border-[var(--border)] pb-7 sm:grid-cols-[minmax(0,1fr)_auto] sm:items-end">
        <div>
          <p className="eyebrow">Đã thanh toán</p>
          <h1 id="order-title" className="m-0 text-4xl font-bold tracking-[-0.045em] sm:text-5xl">
            Đơn #{order.orderId.slice(0, 8).toUpperCase()}
          </h1>
          <p className="mb-0 mt-3 text-[var(--muted)]">
            <time dateTime={order.createdAt}>{timestamp.format(new Date(order.createdAt))}</time>
          </p>
        </div>
        <dl className="m-0 grid gap-1 text-left sm:text-right">
          <dt className="text-sm text-[var(--muted)]">Tổng thanh toán</dt>
          <dd className="m-0 text-2xl font-bold text-[var(--money)]">{money.format(order.totalVnd)} ₫</dd>
        </dl>
      </header>

      <section aria-labelledby="delivery-title">
        <div className="mb-4">
          <p className="eyebrow">Bàn giao an toàn</p>
          <h2 id="delivery-title" className="m-0 text-2xl tracking-[-0.03em]">
            Sản phẩm trong đơn
          </h2>
          <p className="mb-0 mt-2 text-sm text-[var(--muted)]">
            Thông tin nhạy cảm chỉ được tải khi anh chủ động bấm hiển thị và sẽ bị xóa khỏi màn hình khi ẩn lại.
          </p>
        </div>

        <ol className="m-0 grid list-none gap-4 p-0">
          {order.items.map((item) => (
            <li
              key={item.itemId}
              className="rounded-lg border border-[var(--border)] bg-[var(--surface)] p-5 shadow-[var(--shadow-sm)] sm:p-6"
            >
              <article>
                <div className="flex flex-col gap-2 sm:flex-row sm:items-start sm:justify-between sm:gap-6">
                  <div className="min-w-0">
                    <p className="m-0 text-xs font-bold uppercase tracking-[0.08em] text-[var(--muted)]">Sản phẩm số</p>
                    <h3 className="mb-0 mt-1 text-xl">{item.productName}</h3>
                  </div>
                  <strong className="whitespace-nowrap text-[var(--money)]">{money.format(item.unitPriceVnd)} ₫</strong>
                </div>
                <SecretReveal orderId={order.orderId} itemId={item.itemId} />
              </article>
            </li>
          ))}
        </ol>
      </section>
    </main>
  );
}
