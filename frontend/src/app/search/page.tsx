import Link from 'next/link';

import {
  CatalogFilters,
  catalogAvailabilityValues,
  catalogSortValues,
  type CatalogSearchState,
} from '@/components/product/catalog-filters';
import { ProductGrid } from '@/components/product/product-grid';
import { ProductRows } from '@/components/product/product-row';
import { listProducts } from '@/lib/api/catalog';
import type { CatalogCategory, CatalogPage } from '@/lib/contracts/catalog';

type SearchParams = Record<string, string | string[] | undefined>;

export default async function SearchPage({ searchParams }: { searchParams: Promise<SearchParams> }) {
  const state = parseSearchState(await searchParams);
  const query = {
    q: state.q || undefined,
    category: state.category || undefined,
    availability: state.availability,
    sort: state.sort,
    page: state.page - 1,
    size: 24,
  };

  const [catalogResult, categoryResult] = await Promise.allSettled([
    listProducts(query),
    listProducts({ sort: 'name-asc', size: 100 }),
  ]);
  const catalog: CatalogPage | null = catalogResult.status === 'fulfilled' ? catalogResult.value : null;
  const categories = categoryResult.status === 'fulfilled'
    ? uniqueCategories(categoryResult.value.items.map((product) => product.category))
    : [];
  const retryHref = catalogUrl(state);

  return (
    <main className="page-shell search-page">
      <header className="catalog-header">
        <p className="eyebrow">Nexa catalog</p>
        <h1>Chọn đúng sản phẩm số</h1>
        <p>Tìm kiếm, lọc theo tồn kho và so sánh giá trước khi thanh toán bằng số dư ví.</p>
      </header>

      <CatalogFilters
        categories={categories}
        state={state}
        gridHref={catalogUrl({ ...state, view: 'grid' }, true)}
        listHref={catalogUrl({ ...state, view: 'list' }, true)}
      />

      <section className="catalog-results" aria-labelledby="catalog-results-title">
        <div className="results-summary">
          <div>
            <p className="eyebrow">Kết quả</p>
            <h2 id="catalog-results-title">
              {catalog ? `${catalog.page.totalItems} sản phẩm` : 'Danh mục sản phẩm'}
            </h2>
          </div>
          {state.q && <p>Từ khóa: <strong>{state.q}</strong></p>}
        </div>

        {!catalog ? (
          <div className="catalog-state catalog-state-error" role="alert">
            <strong>Chưa thể tải danh mục</strong>
            <p>Dịch vụ sản phẩm đang tạm gián đoạn. Bộ lọc của anh vẫn được giữ lại.</p>
            <Link className="button button-secondary" href={retryHref}>Thử lại</Link>
          </div>
        ) : catalog.items.length === 0 ? (
          <div className="catalog-state" role="status">
            <strong>Không tìm thấy sản phẩm phù hợp</strong>
            <p>Thử từ khóa ngắn hơn hoặc bỏ bớt bộ lọc tồn kho và danh mục.</p>
            <Link className="button button-secondary" href="/search">Xóa bộ lọc</Link>
          </div>
        ) : state.view === 'list' ? (
          <ProductRows products={catalog.items} />
        ) : (
          <ProductGrid products={catalog.items} />
        )}

        {catalog && catalog.page.totalPages > 1 && (
          <nav className="catalog-pagination" aria-label="Phân trang sản phẩm">
            {catalog.page.hasPrevious ? (
              <Link className="button button-secondary" href={catalogUrl({ ...state, page: state.page - 1 })}>Trang trước</Link>
            ) : <span />}
            <span>Trang {state.page} / {catalog.page.totalPages}</span>
            {catalog.page.hasNext ? (
              <Link className="button button-secondary" href={catalogUrl({ ...state, page: state.page + 1 })}>Trang sau</Link>
            ) : <span />}
          </nav>
        )}
      </section>
    </main>
  );
}

function parseSearchState(params: SearchParams): CatalogSearchState {
  const q = single(params.q).trim().slice(0, 200);
  const categoryCandidate = single(params.category).trim();
  const category = /^[a-z0-9]+(?:-[a-z0-9]+)*$/.test(categoryCandidate) && categoryCandidate.length <= 120
    ? categoryCandidate
    : '';
  const availabilityCandidate = single(params.availability);
  const availability = includes(catalogAvailabilityValues, availabilityCandidate) ? availabilityCandidate : 'any';
  const sortCandidate = single(params.sort);
  const sort = includes(catalogSortValues, sortCandidate) ? sortCandidate : 'relevance';
  const view = single(params.view) === 'list' ? 'list' : 'grid';
  const pageCandidate = Number.parseInt(single(params.page), 10);
  const page = Number.isSafeInteger(pageCandidate) && pageCandidate > 0 && pageCandidate <= 10_001 ? pageCandidate : 1;
  return { q, category, availability, sort, view, page };
}

function catalogUrl(state: CatalogSearchState, includeView = false): string {
  const params = new URLSearchParams();
  if (state.q) params.set('q', state.q);
  if (state.category) params.set('category', state.category);
  if (state.availability !== 'any') params.set('availability', state.availability);
  if (state.sort !== 'relevance') params.set('sort', state.sort);
  if (includeView || state.view !== 'grid') params.set('view', state.view);
  if (state.page > 1) params.set('page', String(state.page));
  const query = params.toString();
  return query ? `/search?${query}` : '/search';
}

function single(value: string | string[] | undefined): string {
  return typeof value === 'string' ? value : '';
}

function includes<const T extends string>(values: readonly T[], value: string): value is T {
  return values.includes(value as T);
}

function uniqueCategories(categories: CatalogCategory[]): CatalogCategory[] {
  return [...new Map(categories.map((category) => [category.id, category])).values()]
    .sort((left, right) => left.name.localeCompare(right.name, 'vi'));
}
