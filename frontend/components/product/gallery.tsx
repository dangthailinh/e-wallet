import type { CatalogProductDetail } from '@/lib/contracts/catalog';

export function ProductGallery({ product }: { product: CatalogProductDetail }) {
  const backgroundImage = product.imageUrl ? `url(${JSON.stringify(new URL(product.imageUrl).href)})` : undefined;

  return (
    <figure className="product-gallery">
      <div
        className="product-gallery-visual"
        role="img"
        aria-label={`Minh họa ${product.name}`}
        style={backgroundImage ? { backgroundImage } : undefined}
      >
        {!backgroundImage && (
          <span aria-hidden="true">
            {product.category.name
              .split(/\s+/)
              .slice(0, 2)
              .map((word) => word[0]?.toLocaleUpperCase('vi-VN'))
              .join('')}
          </span>
        )}
      </div>
      <figcaption>Ảnh minh họa · Thông tin nhận hàng chỉ hiển thị sau khi thanh toán</figcaption>
    </figure>
  );
}
