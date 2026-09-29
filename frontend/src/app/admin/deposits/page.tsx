'use client';

import Link from 'next/link';
import { FormEvent, useEffect, useState } from 'react';

import { DepositTable } from '@/components/admin/deposit-table';
import {
  listAdminDeposits,
  type AdminDeposit,
  type AdminDepositFilters,
  type AdminDepositStatus,
  type AdminOperationsPage,
} from '@/lib/api/admin-operations';
import { ApiError } from '@/lib/api/client';

const DEPOSIT_STATUSES: AdminDepositStatus[] = ['PENDING', 'PAID', 'FAILED', 'EXPIRED', 'REVIEW'];

export default function AdminDepositsPage() {
  const [filters, setFilters] = useState<AdminDepositFilters>(() => readDepositFilters());
  const [deposits, setDeposits] = useState<AdminOperationsPage<AdminDeposit>>();
  const [loading, setLoading] = useState(true);
  const [accessDenied, setAccessDenied] = useState(false);
  const [error, setError] = useState('');
  const [filterError, setFilterError] = useState('');

  useEffect(() => {
    let active = true;
    setLoading(true);
    setError('');
    listAdminDeposits(filters)
      .then((value) => active && setDeposits(value))
      .catch((reason: unknown) => {
        if (!active) return;
        if (isDenied(reason)) setAccessDenied(true);
        else setError('Không thể tải danh sách giao dịch nạp tiền quản trị.');
      })
      .finally(() => active && setLoading(false));
    return () => { active = false; };
  }, [filters]);

  function applyFilters(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    const status = parseStatus(form.get('status'));
    const channel = form.get('channel') === 'MOMO' ? 'MOMO' as const : undefined;
    const userId = parseUserId(form.get('userId'));
    if (form.get('status') && !status) { setFilterError('Trạng thái giao dịch không hợp lệ.'); return; }
    if (form.get('userId') && !userId) { setFilterError('Mã người dùng phải là số nguyên dương.'); return; }
    const next: AdminDepositFilters = { page: 0, size: 20, ...(status ? { status } : {}), ...(channel ? { channel } : {}), ...(userId ? { userId } : {}) };
    setFilterError(''); setFilters(next); replaceUrl('/admin/deposits', next);
  }

  function movePage(nextPage: number) {
    const next = { ...filters, page: nextPage };
    setFilters(next); replaceUrl('/admin/deposits', next);
  }

  if (accessDenied) return <AccessDenied />;

  return (
    <main className="page-shell admin-page">
      <header className="admin-header">
        <div>
          <p className="eyebrow">Quản trị vận hành</p>
          <h1>Giao dịch nạp tiền</h1>
          <p>Theo dõi MoMo và các trường hợp cần đối soát. Mã tham chiếu nhà cung cấp, mã giao dịch và hướng dẫn thanh toán không được hiển thị.</p>
        </div>
        <AdminSubnav />
      </header>
      <section className="admin-panel operations-filter-panel" aria-labelledby="deposit-filters-title">
        <div className="admin-panel-heading"><div><p className="eyebrow">Tra cứu</p><h2 id="deposit-filters-title">Bộ lọc giao dịch</h2></div></div>
        <form className="operations-filters" onSubmit={applyFilters}>
          <label className="admin-field" htmlFor="deposit-status">Trạng thái
            <select id="deposit-status" name="status" defaultValue={filters.status ?? ''}><option value="">Tất cả trạng thái</option>{DEPOSIT_STATUSES.map((status) => <option value={status} key={status}>{depositStatusLabel(status)}</option>)}</select>
          </label>
          <label className="admin-field" htmlFor="deposit-channel">Kênh thanh toán
            <select id="deposit-channel" name="channel" defaultValue={filters.channel ?? ''}><option value="">Tất cả kênh</option><option value="MOMO">MoMo</option></select>
          </label>
          <label className="admin-field" htmlFor="deposit-user-id">Mã người dùng
            <input id="deposit-user-id" name="userId" inputMode="numeric" pattern="[0-9]*" defaultValue={filters.userId ?? ''} placeholder="Ví dụ: 42" />
          </label>
          <button className="button button-primary operations-filter-submit" type="submit">Áp dụng bộ lọc</button>
        </form>
        {filterError && <p className="auth-field-error" role="alert">{filterError}</p>}
      </section>
      {error && <section className="admin-panel" role="alert"><p>{error}</p><button className="button button-secondary" type="button" onClick={() => setFilters({ ...filters })}>Thử lại</button></section>}
      <section className="admin-panel" aria-labelledby="deposits-list-title">
        <div className="admin-panel-heading"><div><p className="eyebrow">Dữ liệu an toàn</p><h2 id="deposits-list-title">Danh sách giao dịch</h2></div><span>{deposits ? `${deposits.page.totalItems} giao dịch` : ''}</span></div>
        {loading ? <div className="admin-loading" role="status">Đang tải giao dịch…</div> : <DepositTable deposits={deposits?.items ?? []} />}
        {deposits && <Pagination page={deposits.page} onMove={movePage} />}
      </section>
    </main>
  );
}

function AdminSubnav() { return <nav className="admin-subnav" aria-label="Điều hướng quản trị"><Link href="/admin/products">Sản phẩm</Link><Link href="/admin/inventory">Kho mã hóa</Link><Link href="/admin/orders">Đơn hàng</Link><Link href="/admin/deposits" aria-current="page">Nạp tiền</Link></nav>; }
function Pagination({ page, onMove }: { page: AdminOperationsPage<unknown>['page']; onMove: (page: number) => void }) { if (page.totalPages <= 1) return null; return <nav className="admin-pagination" aria-label="Phân trang giao dịch"><button type="button" disabled={!page.hasPrevious} onClick={() => onMove(page.number - 1)}>Trang trước</button><span>Trang {page.number + 1}/{page.totalPages}</span><button type="button" disabled={!page.hasNext} onClick={() => onMove(page.number + 1)}>Trang sau</button></nav>; }
function AccessDenied() { return <main className="state-page"><p className="eyebrow">Khu vực quản trị</p><h1>Không có quyền truy cập</h1><p role="alert">Tài khoản hiện tại không có quyền truy cập giao dịch nạp tiền.</p></main>; }
function readDepositFilters(): AdminDepositFilters { if (typeof window === 'undefined') return { page: 0, size: 20 }; const query = new URLSearchParams(window.location.search); const status = parseStatus(query.get('status')); const channel = query.get('channel') === 'MOMO' ? 'MOMO' as const : undefined; const userId = parseUserId(query.get('userId')); const page = parsePage(query.get('page')); return { page, size: 20, ...(status ? { status } : {}), ...(channel ? { channel } : {}), ...(userId ? { userId } : {}) }; }
function replaceUrl(path: string, filters: AdminDepositFilters) { const query = new URLSearchParams({ page: String(filters.page ?? 0), size: String(filters.size ?? 20) }); if (filters.status) query.set('status', filters.status); if (filters.channel) query.set('channel', filters.channel); if (filters.userId) query.set('userId', String(filters.userId)); window.history.replaceState({}, '', `${path}?${query}`); }
function parseStatus(value: FormDataEntryValue | string | null): AdminDepositStatus | undefined { return typeof value === 'string' && DEPOSIT_STATUSES.includes(value as AdminDepositStatus) ? value as AdminDepositStatus : undefined; }
function parseUserId(value: FormDataEntryValue | string | null): number | undefined { if (typeof value !== 'string' || value.trim() === '') return undefined; const parsed = Number(value); return Number.isSafeInteger(parsed) && parsed > 0 ? parsed : undefined; }
function parsePage(value: string | null): number { const parsed = Number(value ?? 0); return Number.isSafeInteger(parsed) && parsed >= 0 ? parsed : 0; }
function depositStatusLabel(status: AdminDepositStatus) { return { PENDING: 'Đang chờ', PAID: 'Đã nhận tiền', FAILED: 'Thất bại', EXPIRED: 'Hết hạn', REVIEW: 'Đang rà soát' }[status]; }
function isDenied(error: unknown) { return error instanceof ApiError && (error.status === 401 || error.status === 403); }
