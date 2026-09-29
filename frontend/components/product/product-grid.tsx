import Link from 'next/link';

import type { CatalogProductSummary } from '@/lib/contracts/catalog';

const vnd = new Intl.NumberFormat('vi-VN', {
  style: 'currency',
  currency: 'VND',
  maximumFractionDigits: 0,
});

export function ProductGrid({ products }: { products: CatalogProductSummary[] }) {
  return (
    <div className="catalog-grid">
      {products.map((product) => (
        <article className="catalog-card" key={product.id}>
          <p className="catalog-card-category">{product.category.name}</p>
          <h3><Link href={`/product/${product.slug}`}>{product.name}</Link></h3>
          <div className="catalog-card-meta">
            <strong>{vnd.format(product.priceVnd)}</strong>
            <span className={product.availableCount > 0 ? 'stock-in' : 'stock-out'}>
              {product.availableCount > 0 ? `Còn ${product.availableCount}` : 'Tạm hết hàng'}
            </span>
          </div>
          <Link className="button button-secondary" href={`/product/${product.slug}`}>
            Xem chi tiết
          </Link>
        </article>
      ))}
    </div>
  );
}
