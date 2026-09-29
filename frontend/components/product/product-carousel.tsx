import Link from 'next/link';

import type { CatalogProductSummary } from '@/lib/contracts/catalog';

const vnd = new Intl.NumberFormat('vi-VN', {
  style: 'currency',
  currency: 'VND',
  maximumFractionDigits: 0,
});

export function ProductCarousel({ products }: { products: CatalogProductSummary[] }) {
  if (products.length === 0) return null;

  return (
    <section className="carousel-section" aria-labelledby="latest-title">
      <div className="section-heading">
        <div>
          <p className="eyebrow">Mới cập nhật</p>
          <h2 id="latest-title">Thêm lựa chọn cho anh</h2>
        </div>
      </div>
      <ul className="product-carousel" aria-label="Sản phẩm mới cập nhật">
        {products.map((product) => (
          <li key={product.id}>
            <Link href={`/product/${product.slug}`}>
              <span>{product.category.name}</span>
              <strong>{product.name}</strong>
              <small>{vnd.format(product.priceVnd)}</small>
            </Link>
          </li>
        ))}
      </ul>
    </section>
  );
}
