export type CatalogCategory = {
  id: string;
  name: string;
  slug: string;
};

export type CatalogProductSummary = {
  id: string;
  category: CatalogCategory;
  name: string;
  slug: string;
  priceVnd: number;
  imageUrl: string | null;
  availableCount: number;
};

export type CatalogProductDetail = CatalogProductSummary & {
  description: string;
  createdAt: string;
  updatedAt: string;
};

export type CatalogPage = {
  items: CatalogProductSummary[];
  page: {
    number: number;
    size: number;
    totalItems: number;
    totalPages: number;
    hasNext: boolean;
    hasPrevious: boolean;
  };
};

export type CatalogAvailability = 'any' | 'in-stock' | 'out-of-stock';
export type CatalogSort = 'relevance' | 'newest' | 'price-asc' | 'price-desc' | 'name-asc';

export type CatalogQueryInput = {
  q?: string;
  category?: string;
  availability?: CatalogAvailability;
  sort?: CatalogSort;
  page?: number;
  size?: number;
};

const UUID_PATTERN = /^[0-9a-f]{8}-(?:[0-9a-f]{4}-){3}[0-9a-f]{12}$/i;
const SLUG_PATTERN = /^[a-z0-9]+(?:-[a-z0-9]+)*$/;

export function parseCatalogPage(value: unknown): CatalogPage {
  const source = record(value);
  if (!Array.isArray(source.items)) invalid();
  const page = record(source.page);
  return {
    items: source.items.map(parseProductSummary),
    page: {
      number: integer(page.number, 0),
      size: integer(page.size, 1),
      totalItems: integer(page.totalItems, 0),
      totalPages: integer(page.totalPages, 0),
      hasNext: boolean(page.hasNext),
      hasPrevious: boolean(page.hasPrevious),
    },
  };
}

export function parseProductDetail(value: unknown): CatalogProductDetail {
  const source = record(value);
  return {
    ...parseProductSummary(source),
    description: text(source.description),
    createdAt: isoInstant(source.createdAt),
    updatedAt: isoInstant(source.updatedAt),
  };
}

function parseProductSummary(value: unknown): CatalogProductSummary {
  const source = record(value);
  const category = record(source.category);
  return {
    id: uuid(source.id),
    category: {
      id: uuid(category.id),
      name: text(category.name),
      slug: slug(category.slug),
    },
    name: text(source.name),
    slug: slug(source.slug),
    priceVnd: integer(source.priceVnd, 1),
    imageUrl: nullableHttpsUrl(source.imageUrl),
    availableCount: integer(source.availableCount, 0),
  };
}

function record(value: unknown): Record<string, unknown> {
  if (!value || typeof value !== 'object' || Array.isArray(value)) invalid();
  return value as Record<string, unknown>;
}

function text(value: unknown): string {
  if (typeof value !== 'string' || value.trim() === '') invalid();
  return value;
}

function uuid(value: unknown): string {
  const parsed = text(value);
  if (!UUID_PATTERN.test(parsed)) invalid();
  return parsed;
}

function slug(value: unknown): string {
  const parsed = text(value);
  if (!SLUG_PATTERN.test(parsed)) invalid();
  return parsed;
}

function integer(value: unknown, minimum: number): number {
  if (typeof value !== 'number' || !Number.isSafeInteger(value) || value < minimum) invalid();
  return value;
}

function boolean(value: unknown): boolean {
  if (typeof value !== 'boolean') invalid();
  return value;
}

function nullableHttpsUrl(value: unknown): string | null {
  if (value === null) return null;
  const parsed = text(value);
  let url: URL;
  try {
    url = new URL(parsed);
  } catch {
    return invalid();
  }
  if (url.protocol !== 'https:' || url.username || url.password) invalid();
  return parsed;
}

function isoInstant(value: unknown): string {
  const parsed = text(value);
  if (!/^\d{4}-\d{2}-\d{2}T/.test(parsed) || Number.isNaN(Date.parse(parsed))) invalid();
  return parsed;
}

function invalid(): never {
  throw new TypeError('Invalid catalog response');
}
