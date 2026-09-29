import type { Metadata } from 'next';
import Link from 'next/link';
import { notFound } from 'next/navigation';

import { ProductGallery } from '@/components/product/gallery';
import { Price } from '@/components/product/price';
import { ProductDescription } from '@/components/product/product-description';
import { ProductGrid } from '@/components/product/product-grid';
import { BuyButton } from '@/components/order/buy-button';
import { getProduct, listProducts } from '@/lib/api/catalog';
import { ApiError } from '@/lib/api/errors';
import type { CatalogProductDetail, CatalogProductSummary } from '@/lib/contracts/catalog';

type ProductPageProps = { params: Promise<{ slug: string }> };

export async function generateMetadata({ params }: ProductPageProps): Promise<Metadata> {
  const { slug } = await params;
  const product = await loadProduct(slug);
  const description = `${product.category.name}: ${product.description}`.slice(0, 160);

  return {
    title: `${product.name} | Nexa Store`,
    description,
    alternates: { canonical: productUrl(product.slug) },
    openGraph: {
      type: 'website',
      title: product.name,
      description,
      url: productUrl(product.slug),
      images: product.imageUrl ? [{ url: product.imageUrl, alt: product.name }] : undefined,
    },
  };
}

export default async function ProductPage({ params }: ProductPageProps) {
  const { slug } = await params;
  const product = await loadProduct(slug);
  const related = await loadRelated(product);
  const jsonLd = productJsonLd(product);

  return (
    <main className="page-shell product-page">
      <nav className="product-breadcrumb" aria-label="Đường dẫn">
        <Link href="/">Trang chủ</Link>
        <span aria-hidden="true">/</span>
        <Link href={`/search?category=${encodeURIComponent(product.category.slug)}`}>{product.category.name}</Link>
        <span aria-hidden="true">/</span>
        <span aria-current="page">{product.name}</span>
      </nav>

      <div className="product-hero">
        <ProductGallery product={product} />
        <section className="product-purchase" aria-labelledby="product-title">
          <p className="eyebrow">{product.category.name}</p>
          <h1 id="product-title">{product.name}</h1>
          <p className="product-lead">{product.description}</p>
          <Price className="product-detail-price" value={product.priceVnd} />
          <p className={product.availableCount > 0 ? 'stock-in' : 'stock-out'}>
            {product.availableCount > 0 ? `Còn ${product.availableCount} sản phẩm` : 'Sản phẩm đang tạm hết hàng'}
          </p>
          <BuyButton product={product} />
          <div className="product-secondary-actions">
            <Link href="/login">Đăng nhập</Link>
            <Link href="/top-up">Nạp tiền</Link>
          </div>
        </section>
      </div>

      <ProductDescription product={product} />

      {related.length > 0 && (
        <section className="related-products" aria-labelledby="related-title">
          <div className="section-heading">
            <div>
              <p className="eyebrow">Cùng danh mục</p>
              <h2 id="related-title">Sản phẩm liên quan</h2>
            </div>
            <Link href={`/search?category=${encodeURIComponent(product.category.slug)}`}>Xem tất cả</Link>
          </div>
          <ProductGrid products={related} />
        </section>
      )}

      <script type="application/ld+json" dangerouslySetInnerHTML={{ __html: jsonLd }} />
    </main>
  );
}

async function loadProduct(slug: string): Promise<CatalogProductDetail> {
  try {
    return await getProduct(slug);
  } catch (error) {
    if (error instanceof ApiError && (error.status === 400 || error.status === 404)) notFound();
    throw error;
  }
}

async function loadRelated(product: CatalogProductDetail): Promise<CatalogProductSummary[]> {
  try {
    const page = await listProducts({ category: product.category.slug, sort: 'newest', size: 5 });
    return page.items.filter((candidate) => candidate.id !== product.id).slice(0, 4);
  } catch {
    return [];
  }
}

function productJsonLd(product: CatalogProductDetail): string {
  return JSON.stringify({
    '@context': 'https://schema.org',
    '@type': 'Product',
    name: product.name,
    description: product.description,
    image: product.imageUrl ?? undefined,
    category: product.category.name,
    url: productUrl(product.slug),
    offers: {
      '@type': 'Offer',
      priceCurrency: 'VND',
      price: product.priceVnd,
      availability: product.availableCount > 0
        ? 'https://schema.org/InStock'
        : 'https://schema.org/OutOfStock',
      url: productUrl(product.slug),
    },
  }).replaceAll('<', '\\u003c');
}

function productUrl(slug: string): string {
  const origin = new URL(process.env.NEXT_PUBLIC_SITE_URL ?? 'http://localhost:3000');
  if (!['http:', 'https:'].includes(origin.protocol) || origin.username || origin.password || origin.pathname !== '/') {
    throw new Error('NEXT_PUBLIC_SITE_URL must be an HTTP(S) origin without credentials or a path');
  }
  return new URL(`/product/${encodeURIComponent(slug)}`, origin).toString();
}
