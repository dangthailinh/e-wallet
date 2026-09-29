import Link from 'next/link';

import type { CatalogCategory, CatalogAvailability, CatalogSort } from '@/lib/contracts/catalog';

export type CatalogSearchState = {
  q: string;
  category: string;
  availability: CatalogAvailability;
  sort: CatalogSort;
  view: 'grid' | 'list';
  page: number;
};

type CatalogFiltersProps = {
  categories: CatalogCategory[];
  state: CatalogSearchState;
  gridHref: string;
  listHref: string;
};

export function CatalogFilters({ categories, state, gridHref, listHref }: CatalogFiltersProps) {
  return (
    <div className="catalog-toolbar">
      <form action="/search" className="catalog-filters" role="search">
        <div className="filter-field filter-query">
          <label htmlFor="catalog-query">Tìm trong danh mục</label>
          <input
            id="catalog-query"
            name="q"
            type="search"
            defaultValue={state.q}
            maxLength={200}
            placeholder="Tên sản phẩm, dịch vụ…"
          />
        </div>

        <div className="filter-field">
          <label htmlFor="catalog-category">Danh mục</label>
          <select id="catalog-category" name="category" defaultValue={state.category}>
            <option value="">Tất cả</option>
            {state.category && !categories.some((category) => category.slug === state.category) && (
              <option value={state.category}>{state.category}</option>
            )}
            {categories.map((category) => (
              <option value={category.slug} key={category.id}>{category.name}</option>
            ))}
          </select>
        </div>

        <div className="filter-field">
          <label htmlFor="catalog-availability">Tồn kho</label>
          <select id="catalog-availability" name="availability" defaultValue={state.availability}>
            <option value="any">Tất cả</option>
            <option value="in-stock">Còn hàng</option>
            <option value="out-of-stock">Tạm hết</option>
          </select>
        </div>

        <div className="filter-field">
          <label htmlFor="catalog-sort">Sắp xếp</label>
          <select id="catalog-sort" name="sort" defaultValue={state.sort}>
            <option value="relevance">Liên quan nhất</option>
            <option value="newest">Mới nhất</option>
            <option value="price-asc">Giá tăng dần</option>
            <option value="price-desc">Giá giảm dần</option>
            <option value="name-asc">Tên A–Z</option>
          </select>
        </div>

        <input type="hidden" name="view" value={state.view} />
        <button className="button button-primary filter-submit" type="submit">Áp dụng</button>
      </form>

      <div className="view-switch" aria-label="Kiểu hiển thị">
        <Link href={gridHref} aria-label="Xem dạng lưới" aria-current={state.view === 'grid' ? 'page' : undefined}>
          Lưới
        </Link>
        <Link href={listHref} aria-label="Xem dạng danh sách" aria-current={state.view === 'list' ? 'page' : undefined}>
          Danh sách
        </Link>
      </div>
    </div>
  );
}

export const catalogAvailabilityValues: readonly CatalogAvailability[] = ['any', 'in-stock', 'out-of-stock'];
export const catalogSortValues: readonly CatalogSort[] = ['relevance', 'newest', 'price-asc', 'price-desc', 'name-asc'];
