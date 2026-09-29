import Link from 'next/link';

import { FeaturedGrid } from '@/components/product/featured-grid';
import { ProductCarousel } from '@/components/product/product-carousel';
import { listProducts } from '@/lib/api/catalog';
import type { CatalogCategory, CatalogPage } from '@/lib/contracts/catalog';

type HomePageProps = {
  searchParams: Promise<{ error?: string }>;
};

export default async function HomePage({ searchParams }: HomePageProps) {
  const params = await searchParams;
  if (params.error === '1') {
    throw new Error('Intentional storefront error preview');
  }

  let catalog: CatalogPage | null = null;
  let catalogFailed = false;
  try {
    catalog = await listProducts({ sort: 'newest', size: 20 });
  } catch {
    catalogFailed = true;
  }

  const categories = uniqueCategories(catalog?.items.map((product) => product.category) ?? []);
  const featured = catalog?.items.slice(0, 10) ?? [];
  const latest = catalog?.items.slice(10, 18) ?? [];

  return (
    <div className="page-shell">
      <section className="hero" aria-labelledby="hero-title">
        <div className="hero-copy">
          <p className="eyebrow">Kho sản phẩm số xác minh</p>
          <h1 id="hero-title">Chọn sản phẩm. Trả bằng ví. Nhận ngay.</h1>
          <p className="hero-description">
            Nạp tiền qua MoMo, thanh toán từ số dư và xem mã sản phẩm an toàn trong lịch sử đơn hàng.
          </p>
          <div className="hero-actions">
            <a className="button button-primary" href="#products">
              Xem sản phẩm
            </a>
            <Link className="button button-secondary" href="/top-up">
              Nạp tiền vào ví
            </Link>
          </div>
        </div>
        <aside className="wallet-preview" aria-label="Quy trình mua hàng">
          <div className="preview-heading">
            <span>Thanh toán an toàn</span>
            <span className="status-pending">Đang phát triển</span>
          </div>
          <ol>
            <li><span>01</span><p><strong>Nạp số dư</strong><small>Xác nhận tự động qua MoMo</small></p></li>
            <li><span>02</span><p><strong>Xác nhận mua</strong><small>Hiển thị số dư trước và sau</small></p></li>
            <li><span>03</span><p><strong>Nhận sản phẩm</strong><small>Chỉ xem trong tài khoản của bạn</small></p></li>
          </ol>
        </aside>
      </section>

      {categories.length > 0 && (
        <nav className="category-strip" aria-label="Danh mục sản phẩm">
          <Link href="/search">Tất cả</Link>
          {categories.map((category) => (
            <Link href={`/search?category=${encodeURIComponent(category.slug)}`} key={category.id}>
              {category.name}
            </Link>
          ))}
        </nav>
      )}

      <section id="products" className="catalog-section" aria-labelledby="products-title">
        <div className="section-heading">
          <div>
            <p className="eyebrow">Danh mục mới nhất</p>
            <h2 id="products-title">Sản phẩm nổi bật</h2>
          </div>
          <Link href="/search">Xem tất cả <span aria-hidden="true">→</span></Link>
        </div>

        {catalogFailed ? (
          <div className="catalog-state catalog-state-error" role="alert">
            <strong>Chưa thể tải sản phẩm</strong>
            <p>Dịch vụ catalog đang tạm gián đoạn. Anh có thể thử tải lại trang.</p>
            <Link className="button button-secondary" href="/">Thử lại</Link>
          </div>
        ) : featured.length === 0 ? (
          <div className="catalog-state" role="status">
            <strong>Chưa có sản phẩm được mở bán</strong>
            <p>Danh mục đang được cập nhật. Anh quay lại sau nhé.</p>
          </div>
        ) : (
          <FeaturedGrid products={featured} />
        )}
      </section>

      <ProductCarousel products={latest} />

      <section className="trust-strip" aria-label="Cam kết dịch vụ">
        <p><strong>Giao tự động</strong><span>Sản phẩm xuất kho ngay khi ví thanh toán thành công.</span></p>
        <p><strong>Dữ liệu riêng tư</strong><span>Mã bí mật không được gửi qua email.</span></p>
        <p><strong>Lịch sử minh bạch</strong><span>Số dư và đơn hàng có đối soát rõ ràng.</span></p>
      </section>
    </div>
  );
}

function uniqueCategories(categories: CatalogCategory[]): CatalogCategory[] {
  return [...new Map(categories.map((category) => [category.id, category])).values()];
}
