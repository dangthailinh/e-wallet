'use client';

import { useCallback, useEffect, useState } from 'react';
import Link from 'next/link';

import { CategoryManager } from '@/components/admin/category-manager';
import { ProductForm } from '@/components/admin/product-form';
import {
  changeProductStatus,
  createProduct,
  listAdminCategories,
  listAdminProducts,
  updateProduct,
  type AdminCategory,
  type AdminPage,
  type AdminProduct,
  type ProductInput,
} from '@/lib/api/admin-catalog';
import { ApiError } from '@/lib/api/client';

export default function AdminProductsPage() {
  const [categories, setCategories] = useState<AdminCategory[]>([]);
  const [products, setProducts] = useState<AdminPage<AdminProduct>>();
  const [page, setPage] = useState(0);
  const [editing, setEditing] = useState<AdminProduct>();
  const [creating, setCreating] = useState(false);
  const [pendingId, setPendingId] = useState<string>();
  const [loading, setLoading] = useState(true);
  const [accessDenied, setAccessDenied] = useState(false);
  const [error, setError] = useState('');

  const load = useCallback(async (pageNumber: number) => {
    setLoading(true);
    setError('');
    try {
      const [categoryPage, productPage] = await Promise.all([
        listAdminCategories(),
        listAdminProducts(pageNumber),
      ]);
      setCategories(categoryPage.items);
      setProducts(productPage);
      setAccessDenied(false);
    } catch (reason) {
      if (isDenied(reason)) setAccessDenied(true);
      else setError('Không thể tải catalog quản trị.');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { void load(page); }, [load, page]);

  async function saveProduct(input: ProductInput) {
    if (editing) await updateProduct(editing.id, input);
    else await createProduct(input);
    setEditing(undefined);
    setCreating(false);
    await load(page);
  }

  async function toggleProduct(product: AdminProduct) {
    setPendingId(product.id);
    setError('');
    try {
      const updated = await changeProductStatus(
        product.id,
        product.status === 'ACTIVE' ? 'INACTIVE' : 'ACTIVE'
      );
      setProducts((current) => current && ({
        ...current,
        items: current.items.map((item) => item.id === updated.id ? updated : item),
      }));
    } catch (reason) {
      if (isDenied(reason)) setAccessDenied(true);
      else setError('Không thể đổi trạng thái sản phẩm.');
    } finally {
      setPendingId(undefined);
    }
  }

  function updateCategory(category: AdminCategory) {
    setCategories((current) => [...current.filter((item) => item.id !== category.id), category]
      .sort((a, b) => a.displayOrder - b.displayOrder || a.name.localeCompare(b.name, 'vi')));
    setProducts((current) => current && ({
      ...current,
      items: current.items.map((product) => product.category.id === category.id ? {
        ...product,
        category: { id: category.id, name: category.name, slug: category.slug },
      } : product),
    }));
  }

  if (accessDenied) {
    return (
      <main className="state-page">
        <p className="eyebrow">Khu vực quản trị</p>
        <h1>Không có quyền truy cập</h1>
        <p role="alert">Tài khoản hiện tại không có quyền quản lý catalog.</p>
      </main>
    );
  }

  return (
    <main className="page-shell admin-page">
      <header className="admin-header">
        <div>
          <p className="eyebrow">Quản trị catalog</p>
          <h1>Sản phẩm số</h1>
          <p>Tạo, chỉnh sửa và đóng mở bán mà không làm thay đổi giá từ phía trình duyệt.</p>
        </div>
        <nav className="admin-subnav" aria-label="Điều hướng quản trị">
          <Link aria-current="page" href="/admin/products">Sản phẩm</Link>
          <Link href="/admin/inventory">Kho mã hóa</Link>
          <Link href="/admin/orders">Đơn hàng</Link>
          <Link href="/admin/deposits">Nạp tiền</Link>
        </nav>
      </header>

      {error && <p className="auth-request-error" role="alert">{error}</p>}
      <section className="admin-panel" aria-labelledby="product-list-title">
        <div className="admin-panel-heading">
          <div>
            <p className="eyebrow">Danh sách quản trị</p>
            <h2 id="product-list-title">Sản phẩm</h2>
          </div>
          <button className="button button-primary" type="button" onClick={() => { setEditing(undefined); setCreating(true); }}>
            Thêm sản phẩm
          </button>
        </div>

        {loading ? (
          <div className="admin-loading" role="status">Đang tải catalog…</div>
        ) : products?.items.length ? (
          <div className="admin-table-scroll">
            <table className="admin-table">
              <thead><tr><th>Sản phẩm</th><th>Danh mục</th><th>Giá</th><th>Trạng thái</th><th>Thao tác</th></tr></thead>
              <tbody>
                {products.items.map((product) => (
                  <tr key={product.id}>
                    <td><strong>{product.name}</strong><small>/{product.slug}</small></td>
                    <td>{product.category.name}</td>
                    <td>{formatVnd(product.priceVnd)}</td>
                    <td><span className={`admin-status admin-status-${product.status.toLowerCase()}`}>
                      {product.status === 'ACTIVE' ? 'Đang bán' : 'Ngừng bán'}
                    </span></td>
                    <td><div className="admin-row-actions">
                      <button type="button" aria-label={`Sửa ${product.name}`} onClick={() => { setCreating(false); setEditing(product); }}>Sửa</button>
                      <button type="button" disabled={pendingId === product.id} aria-label={`${product.status === 'ACTIVE' ? 'Ngừng bán' : 'Mở bán'} ${product.name}`} onClick={() => void toggleProduct(product)}>
                        {product.status === 'ACTIVE' ? 'Ngừng bán' : 'Mở bán'}
                      </button>
                    </div></td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ) : <p className="admin-empty">Chưa có sản phẩm. Hãy tạo sản phẩm đầu tiên.</p>}

        {products && products.page.totalPages > 1 && (
          <nav className="admin-pagination" aria-label="Phân trang sản phẩm">
            <button type="button" disabled={!products.page.hasPrevious} onClick={() => setPage((value) => value - 1)}>Trang trước</button>
            <span>Trang {products.page.number + 1}/{products.page.totalPages}</span>
            <button type="button" disabled={!products.page.hasNext} onClick={() => setPage((value) => value + 1)}>Trang sau</button>
          </nav>
        )}
      </section>

      {(creating || editing) && (
        <section className="admin-panel admin-editor" aria-labelledby="product-editor-title">
          <div className="admin-panel-heading"><div><p className="eyebrow">Biểu mẫu</p><h2 id="product-editor-title">{editing ? 'Sửa sản phẩm' : 'Thêm sản phẩm'}</h2></div></div>
          <ProductForm key={editing?.id ?? 'new'} categories={categories} product={editing} onSave={saveProduct} onCancel={() => { setCreating(false); setEditing(undefined); }} />
        </section>
      )}

      <CategoryManager categories={categories} onChange={updateCategory} />
    </main>
  );
}

const formatVnd = (value: number) => `${new Intl.NumberFormat('vi-VN').format(value)} ₫`;
const isDenied = (error: unknown) => error instanceof ApiError && (error.status === 401 || error.status === 403);
