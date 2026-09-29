import Link from 'next/link';

import type { CatalogProductSummary } from '@/lib/contracts/catalog';

const vnd = new Intl.NumberFormat('vi-VN', {
  style: 'currency',
  currency: 'VND',
  maximumFractionDigits: 0,
});

export function FeaturedGrid({ products }: { products: CatalogProductSummary[] }) {
  return (
    <div className="product-list">
      {products.map((product) => (
        <article className="product-row" key={product.id}>
          <div className="product-mark" aria-hidden="true">
            {categoryMark(product.category.name)}
          </div>
          <div className="product-copy">
            <h3>
              <Link href={`/product/${product.slug}`}>{product.name}</Link>
            </h3>
            <p>{product.category.name} · Giao trong tài khoản sau khi thanh toán</p>
          </div>
          <div className="product-price">
            <strong>{vnd.format(product.priceVnd)}</strong>
            <span>{product.availableCount > 0 ? `Còn ${product.availableCount}` : 'Tạm hết hàng'}</span>
          </div>
          <Link className="buy-link" href={`/product/${product.slug}`} aria-label={`Xem ${product.name}`}>
            Xem chi tiết
          </Link>
        </article>
      ))}
    </div>
  );
}

function categoryMark(name: string): string {
  return name
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((word) => word[0]?.toLocaleUpperCase('vi-VN'))
    .join('');
}
