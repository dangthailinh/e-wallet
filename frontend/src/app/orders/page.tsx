'use client';

import Link from 'next/link';
import { useEffect, useState } from 'react';

import { ApiError } from '@/lib/api/errors';
import { listOrders, type OrderList } from '@/lib/api/orders';

const money = new Intl.NumberFormat('vi-VN');
const timestamp = new Intl.DateTimeFormat('vi-VN', { dateStyle: 'medium', timeStyle: 'short' });

export default function OrdersPage() {
  const [page, setPage] = useState(0);
  const [attempt, setAttempt] = useState(0);
  const [orders, setOrders] = useState<OrderList>();
  const [error, setError] = useState<'auth' | 'generic'>();

  useEffect(() => {
    let active = true;
    setOrders(undefined);
    setError(undefined);
    listOrders(page)
      .then((value) => active && setOrders(value))
      .catch((reason: unknown) => {
        if (active) setError(reason instanceof ApiError && reason.status === 401 ? 'auth' : 'generic');
      });
    return () => {
      active = false;
    };
  }, [attempt, page]);

  return (
    <main className="page-shell py-10 sm:py-14" aria-labelledby="orders-title">
      <header className="mb-8 flex flex-col gap-4 border-b border-[var(--border)] pb-7 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <p className="eyebrow">Tài khoản</p>
          <h1 id="orders-title" className="m-0 text-4xl font-bold tracking-[-0.045em] sm:text-5xl">
            Đơn hàng của anh
          </h1>
          <p className="mb-0 mt-3 max-w-2xl text-[var(--muted)]">
            Xem giao dịch đã hoàn tất và mở thông tin bàn giao ngay trong tài khoản.
          </p>
        </div>
        <Link className="button button-secondary self-start sm:self-auto" href="/">
          Tiếp tục mua hàng
        </Link>
      </header>

      {!orders && !error && (
        <p
          className="rounded-lg border border-[var(--border)] bg-[var(--surface)] p-6 text-[var(--muted)]"
          role="status"
        >
          Đang tải lịch sử đơn hàng…
        </p>
      )}

      {error && (
        <section className="rounded-lg border border-[var(--border)] bg-[var(--surface)] p-6" role="alert">
          <h2 className="m-0 text-xl">{error === 'auth' ? 'Cần đăng nhập' : 'Chưa tải được đơn hàng'}</h2>
          <p className="mb-5 mt-2 text-[var(--muted)]">
            {error === 'auth'
              ? 'Phiên đăng nhập đã hết hạn. Đăng nhập lại để xem đơn hàng của anh.'
              : 'Kết nối đang gián đoạn. Anh có thể thử tải lại mà không ảnh hưởng dữ liệu.'}
          </p>
          {error === 'auth' ? (
            <Link className="button button-primary" href="/login">
              Đăng nhập lại
            </Link>
          ) : (
            <button className="button button-primary" type="button" onClick={() => setAttempt((value) => value + 1)}>
              Tải lại
            </button>
          )}
        </section>
      )}

      {orders?.items.length === 0 && (
        <section
          className="rounded-lg border border-dashed border-[var(--border-strong)] p-8 text-center"
          role="status"
        >
          <h2 className="m-0 text-2xl">Chưa có đơn hàng</h2>
          <p className="mb-5 mt-2 text-[var(--muted)]">Sản phẩm đã mua sẽ xuất hiện tại đây.</p>
          <Link className="button button-primary" href="/search">
            Xem sản phẩm
          </Link>
        </section>
      )}

      {orders && orders.items.length > 0 && (
        <>
          <ol className="m-0 grid list-none gap-3 p-0">
            {orders.items.map((order) => (
              <li
                key={order.orderId}
                className="rounded-lg border border-[var(--border)] bg-[var(--surface)] p-5 shadow-[var(--shadow-sm)]"
              >
                <article className="grid gap-4 sm:grid-cols-[minmax(0,1fr)_auto] sm:items-center">
                  <div className="min-w-0">
                    <div className="mb-2 flex flex-wrap items-center gap-2">
                      <span className="rounded-full bg-[var(--surface-subtle)] px-2.5 py-1 text-xs font-bold text-[var(--success)]">
                        Hoàn tất
                      </span>
                      <time className="text-sm text-[var(--muted)]" dateTime={order.createdAt}>
                        {timestamp.format(new Date(order.createdAt))}
                      </time>
                    </div>
                    <h2 className="m-0 truncate text-lg font-semibold">
                      Đơn #{order.orderId.slice(0, 8).toUpperCase()}
                    </h2>
                    <p className="mb-0 mt-1 text-sm text-[var(--muted)]">Mã đầy đủ: {order.orderId}</p>
                  </div>
                  <div className="flex items-center justify-between gap-5 sm:justify-end">
                    <strong className="whitespace-nowrap text-lg text-[var(--money)]">
                      {money.format(order.totalVnd)} ₫
                    </strong>
                    <Link className="button button-secondary whitespace-nowrap" href={`/orders/${order.orderId}`}>
                      Xem chi tiết
                    </Link>
                  </div>
                </article>
              </li>
            ))}
          </ol>

          {orders.totalPages > 1 && (
            <nav className="mt-6 flex items-center justify-between gap-4" aria-label="Phân trang đơn hàng">
              <button
                className="button button-secondary"
                type="button"
                disabled={orders.page === 0}
                onClick={() => setPage((value) => Math.max(0, value - 1))}
              >
                Trang trước
              </button>
              <span className="text-sm text-[var(--muted)]">
                Trang {orders.page + 1}/{orders.totalPages}
              </span>
              <button
                className="button button-secondary"
                type="button"
                disabled={orders.page + 1 >= orders.totalPages}
                onClick={() => setPage((value) => value + 1)}
              >
                Trang sau
              </button>
            </nav>
          )}
        </>
      )}
    </main>
  );
}
