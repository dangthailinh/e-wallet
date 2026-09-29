'use client';

import Link from 'next/link';
import { FormEvent, useEffect, useState } from 'react';

import { OrderTable } from '@/components/admin/order-table';
import {
  listAdminOrders,
  type AdminOperationsPage,
  type AdminOrder,
  type AdminOrderFilters,
  type AdminOrderStatus,
} from '@/lib/api/admin-operations';
import { ApiError } from '@/lib/api/client';

const ORDER_STATUSES: AdminOrderStatus[] = ['COMPLETED', 'FAILED', 'REFUNDED'];

export default function AdminOrdersPage() {
  const [filters, setFilters] = useState<AdminOrderFilters>(() => readOrderFilters());
  const [orders, setOrders] = useState<AdminOperationsPage<AdminOrder>>();
  const [loading, setLoading] = useState(true);
  const [accessDenied, setAccessDenied] = useState(false);
  const [error, setError] = useState('');
  const [filterError, setFilterError] = useState('');

  useEffect(() => {
    let active = true;
    setLoading(true);
    setError('');
    listAdminOrders(filters)
      .then((value) => active && setOrders(value))
      .catch((reason: unknown) => {
        if (!active) return;
        if (isDenied(reason)) setAccessDenied(true);
        else setError('Không thể tải danh sách đơn hàng quản trị.');
      })
      .finally(() => active && setLoading(false));
    return () => { active = false; };
  }, [filters]);

  function applyFilters(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    const status = parseStatus(form.get('status'));
    const userId = parseUserId(form.get('userId'));
    if (form.get('status') && !status) {
      setFilterError('Trạng thái đơn hàng không hợp lệ.');
      return;
    }
    if (form.get('userId') && !userId) {
      setFilterError('Mã người dùng phải là số nguyên dương.');
      return;
    }
    const next = { page: 0, size: 20, ...(status ? { status } : {}), ...(userId ? { userId } : {}) };
    setFilterError('');
    setFilters(next);
    replaceUrl('/admin/orders', next);
  }

  function movePage(nextPage: number) {
    const next = { ...filters, page: nextPage };
    setFilters(next);
    replaceUrl('/admin/orders', next);
  }

  if (accessDenied) return <AccessDenied message="Tài khoản hiện tại không có quyền truy cập đơn hàng." />;

  return (
    <main className="page-shell admin-page">
      <header className="admin-header">
        <div>
          <p className="eyebrow">Quản trị vận hành</p>
          <h1>Đơn hàng</h1>
          <p>Điều tra trạng thái, số tiền và mốc thời gian. Thông tin sản phẩm và dữ liệu bàn giao không xuất hiện tại đây.</p>
        </div>
        <AdminSubnav current="orders" />
      </header>

      <section className="admin-panel operations-filter-panel" aria-labelledby="order-filters-title">
        <div className="admin-panel-heading"><div><p className="eyebrow">Tra cứu</p><h2 id="order-filters-title">Bộ lọc đơn hàng</h2></div></div>
        <form className="operations-filters" onSubmit={applyFilters}>
          <label className="admin-field" htmlFor="order-status">Trạng thái
            <select id="order-status" name="status" defaultValue={filters.status ?? ''}>
              <option value="">Tất cả trạng thái</option>
              {ORDER_STATUSES.map((status) => <option value={status} key={status}>{orderStatusLabel(status)}</option>)}
            </select>
          </label>
          <label className="admin-field" htmlFor="order-user-id">Mã người dùng
            <input id="order-user-id" name="userId" inputMode="numeric" pattern="[0-9]*" defaultValue={filters.userId ?? ''} placeholder="Ví dụ: 42" />
          </label>
          <button className="button button-primary operations-filter-submit" type="submit">Áp dụng bộ lọc</button>
        </form>
        {filterError && <p className="auth-field-error" role="alert">{filterError}</p>}
      </section>

      {error && <section className="admin-panel" role="alert"><p>{error}</p><button className="button button-secondary" type="button" onClick={() => setFilters({ ...filters })}>Thử lại</button></section>}
      <section className="admin-panel" aria-labelledby="orders-list-title">
        <div className="admin-panel-heading"><div><p className="eyebrow">Dữ liệu an toàn</p><h2 id="orders-list-title">Danh sách đơn hàng</h2></div><span>{orders ? `${orders.page.totalItems} đơn` : ''}</span></div>
        {loading ? <div className="admin-loading" role="status">Đang tải đơn hàng…</div> : <OrderTable orders={orders?.items ?? []} />}
        {orders && <Pagination page={orders.page} onMove={movePage} label="đơn hàng" />}
      </section>
    </main>
  );
}

function AdminSubnav({ current }: { current: string }) {
  return <nav className="admin-subnav" aria-label="Điều hướng quản trị">
    <Link href="/admin/products">Sản phẩm</Link><Link href="/admin/inventory">Kho mã hóa</Link>
    <Link href="/admin/orders" aria-current={current === 'orders' ? 'page' : undefined}>Đơn hàng</Link>
    <Link href="/admin/deposits">Nạp tiền</Link>
  </nav>;
}

function Pagination({ page, onMove, label }: { page: AdminOperationsPage<unknown>['page']; onMove: (page: number) => void; label: string }) {
  if (page.totalPages <= 1) return null;
  return <nav className="admin-pagination" aria-label={`Phân trang ${label}`}>
    <button type="button" disabled={!page.hasPrevious} onClick={() => onMove(page.number - 1)}>Trang trước</button>
    <span>Trang {page.number + 1}/{page.totalPages}</span>
    <button type="button" disabled={!page.hasNext} onClick={() => onMove(page.number + 1)}>Trang sau</button>
  </nav>;
}

function AccessDenied({ message }: { message: string }) {
  return <main className="state-page"><p className="eyebrow">Khu vực quản trị</p><h1>Không có quyền truy cập</h1><p role="alert">{message}</p></main>;
}

function readOrderFilters(): AdminOrderFilters {
  if (typeof window === 'undefined') return { page: 0, size: 20 };
  const query = new URLSearchParams(window.location.search);
  const status = parseStatus(query.get('status'));
  const userId = parseUserId(query.get('userId'));
  const page = parsePage(query.get('page'));
  return { page, size: 20, ...(status ? { status } : {}), ...(userId ? { userId } : {}) };
}

function replaceUrl(path: string, filters: AdminOrderFilters) {
  const query = new URLSearchParams({ page: String(filters.page ?? 0), size: String(filters.size ?? 20) });
  if (filters.status) query.set('status', filters.status);
  if (filters.userId) query.set('userId', String(filters.userId));
  window.history.replaceState({}, '', `${path}?${query}`);
}

function parseStatus(value: FormDataEntryValue | string | null): AdminOrderStatus | undefined {
  return typeof value === 'string' && ORDER_STATUSES.includes(value as AdminOrderStatus) ? value as AdminOrderStatus : undefined;
}
function parseUserId(value: FormDataEntryValue | string | null): number | undefined {
  if (typeof value !== 'string' || value.trim() === '') return undefined;
  const parsed = Number(value);
  return Number.isSafeInteger(parsed) && parsed > 0 ? parsed : undefined;
}
function parsePage(value: string | null): number {
  const parsed = Number(value ?? 0);
  return Number.isSafeInteger(parsed) && parsed >= 0 ? parsed : 0;
}
function orderStatusLabel(status: AdminOrderStatus) {
  return { COMPLETED: 'Hoàn tất', FAILED: 'Thất bại', REFUNDED: 'Đã hoàn tiền' }[status];
}
function isDenied(error: unknown) { return error instanceof ApiError && (error.status === 401 || error.status === 403); }
