import Link from 'next/link';

import type { CatalogProductSummary } from '@/lib/contracts/catalog';

const vnd = new Intl.NumberFormat('vi-VN', {
  style: 'currency',
  currency: 'VND',
  maximumFractionDigits: 0,
});

export function ProductRows({ products }: { products: CatalogProductSummary[] }) {
  return (
    <div className="catalog-rows">
      {products.map((product) => (
        <article className="catalog-result-row" key={product.id}>
          <div>
            <p>{product.category.name}</p>
            <h3><Link href={`/product/${product.slug}`}>{product.name}</Link></h3>
          </div>
          <strong>{vnd.format(product.priceVnd)}</strong>
          <span className={product.availableCount > 0 ? 'stock-in' : 'stock-out'}>
            {product.availableCount > 0 ? `Còn ${product.availableCount}` : 'Tạm hết hàng'}
          </span>
          <Link className="buy-link" href={`/product/${product.slug}`} aria-label={`Xem ${product.name}`}>
            Chi tiết
          </Link>
        </article>
      ))}
    </div>
  );
}
