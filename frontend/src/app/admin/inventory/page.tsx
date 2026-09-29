'use client';

import { useCallback, useEffect, useState } from 'react';
import Link from 'next/link';

import { InventoryImportForm } from '@/components/admin/inventory-import-form';
import { InventoryTable } from '@/components/admin/inventory-table';
import {
  changeInventoryStatus,
  importInventory,
  listAdminProducts,
  listInventory,
  type AdminInventoryItem,
  type AdminInventoryList,
  type InventoryImportResponse,
} from '@/lib/api/admin-inventory';
import { ApiError } from '@/lib/api/client';
import type { CatalogProductSummary } from '@/lib/contracts/catalog';

export default function AdminInventoryPage() {
  const [products, setProducts] = useState<CatalogProductSummary[]>([]);
  const [productId, setProductId] = useState('');
  const [inventory, setInventory] = useState<AdminInventoryList>();
  const [loading, setLoading] = useState(true);
  const [accessDenied, setAccessDenied] = useState(false);
  const [error, setError] = useState<string>();
  const [pendingItemId, setPendingItemId] = useState<string>();

  useEffect(() => {
    let active = true;
    listAdminProducts()
      .then((items) => {
        if (!active) return;
        setProducts(items);
        setProductId(items[0]?.id ?? '');
        setLoading(items.length > 0);
      })
      .catch((reason: unknown) => {
        if (!active) return;
        if (isDenied(reason)) setAccessDenied(true);
        else setError('Không thể tải danh sách sản phẩm.');
        setLoading(false);
      });
    return () => { active = false; };
  }, []);

  const refreshInventory = useCallback(async (selectedProductId: string) => {
    if (!selectedProductId) return;
    setLoading(true);
    setError(undefined);
    try {
      setInventory(await listInventory(selectedProductId));
    } catch (reason) {
      if (isDenied(reason)) setAccessDenied(true);
      else setError('Không thể tải kho sản phẩm.');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void refreshInventory(productId);
  }, [productId, refreshInventory]);

  async function handleImport(secrets: string[]): Promise<InventoryImportResponse> {
    const response = await importInventory(productId, secrets);
    await refreshInventory(productId);
    return response;
  }

  async function handleStatusChange(item: AdminInventoryItem, status: 'AVAILABLE' | 'DISABLED') {
    setPendingItemId(item.id);
    setError(undefined);
    try {
      await changeInventoryStatus(item.id, status);
      await refreshInventory(productId);
    } catch (reason) {
      if (isDenied(reason)) setAccessDenied(true);
      else setError('Không thể đổi trạng thái item.');
    } finally {
      setPendingItemId(undefined);
    }
  }

  if (accessDenied) {
    return (
      <main className="state-page">
        <p className="eyebrow">Khu vực quản trị</p>
        <h1>Không có quyền truy cập</h1>
        <p role="alert">Tài khoản hiện tại không có quyền truy cập kho sản phẩm.</p>
      </main>
    );
  }

  return (
    <main className="page-shell admin-page">
      <header className="admin-header">
        <div>
          <p className="eyebrow">Quản trị</p>
          <h1>Kho sản phẩm mã hóa</h1>
          <p>Danh sách chỉ hiển thị trạng thái, phiên bản khóa và sáu ký tự cuối checksum.</p>
        </div>
        <div className="admin-header-tools">
          <nav className="admin-subnav" aria-label="Điều hướng quản trị">
            <Link href="/admin/products">Sản phẩm</Link><Link aria-current="page" href="/admin/inventory">Kho mã hóa</Link>
            <Link href="/admin/orders">Đơn hàng</Link><Link href="/admin/deposits">Nạp tiền</Link>
          </nav>
          <div className="filter-field admin-product-picker">
            <label htmlFor="inventory-product">Sản phẩm</label>
            <select id="inventory-product" value={productId} onChange={(event) => setProductId(event.target.value)}>
              {products.length === 0 && <option value="">Chưa có sản phẩm</option>}
              {products.map((product) => <option value={product.id} key={product.id}>{product.name}</option>)}
            </select>
          </div>
        </div>
      </header>

      {error && <p className="auth-request-error" role="alert">{error}</p>}
      <div className="inventory-counts" aria-label="Tổng hợp tồn kho">
        <strong>{inventory?.counts.AVAILABLE ?? 0}<span> khả dụng</span></strong>
        <strong>{inventory?.counts.RESERVED ?? 0}<span> đã giữ</span></strong>
        <strong>{inventory?.counts.SOLD ?? 0}<span> đã bán</span></strong>
        <strong>{inventory?.counts.DISABLED ?? 0}<span> vô hiệu</span></strong>
      </div>

      <InventoryImportForm disabled={!productId} onImport={handleImport} />

      <section className="admin-panel" aria-labelledby="inventory-list-title">
        <div className="admin-panel-heading">
          <div><p className="eyebrow">Metadata an toàn</p><h2 id="inventory-list-title">Danh sách item</h2></div>
          <span>{loading ? 'Đang tải…' : `${inventory?.items.length ?? 0} item`}</span>
        </div>
        {!loading && <InventoryTable items={inventory?.items ?? []} pendingItemId={pendingItemId} onStatusChange={handleStatusChange} />}
      </section>
    </main>
  );
}

function isDenied(error: unknown): boolean {
  return error instanceof ApiError && (error.status === 401 || error.status === 403);
}
