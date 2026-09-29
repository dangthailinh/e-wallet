import { apiRequest } from './client';

export type CatalogStatus = 'ACTIVE' | 'INACTIVE';
export type AdminPage<T> = {
  items: T[];
  page: { number: number; size: number; totalItems: number; totalPages: number; hasNext: boolean; hasPrevious: boolean };
};
export type AdminCategory = {
  id: string;
  name: string;
  slug: string;
  displayOrder: number;
  status: CatalogStatus;
  createdAt: string;
  updatedAt: string;
  version: number;
};
export type AdminProduct = {
  id: string;
  category: Pick<AdminCategory, 'id' | 'name' | 'slug'>;
  name: string;
  slug: string;
  description: string;
  priceVnd: number;
  status: CatalogStatus;
  imageUrl: string | null;
  createdAt: string;
  updatedAt: string;
  version: number;
};
export type CategoryInput = { name: string; slug: string; displayOrder: number };
export type ProductInput = {
  categoryId: string;
  name: string;
  slug: string;
  description: string;
  priceVnd: number;
  status: CatalogStatus;
  imageUrl: string | null;
};

export async function listAdminCategories(page = 0, size = 100): Promise<AdminPage<AdminCategory>> {
  return parsePage(await apiRequest<unknown>(`/admin/categories?page=${page}&size=${size}`), parseCategory);
}

export async function listAdminProducts(page = 0, size = 20): Promise<AdminPage<AdminProduct>> {
  return parsePage(await apiRequest<unknown>(`/admin/products?page=${page}&size=${size}`), parseProduct);
}

export async function createCategory(input: CategoryInput): Promise<AdminCategory> {
  return parseCategory(await apiRequest<unknown>('/admin/categories', { method: 'POST', body: input }));
}

export async function updateCategory(id: string, input: CategoryInput): Promise<AdminCategory> {
  return parseCategory(await apiRequest<unknown>(`/admin/categories/${encodeURIComponent(id)}`, {
    method: 'PUT', body: input,
  }));
}

export async function changeCategoryStatus(id: string, status: CatalogStatus): Promise<AdminCategory> {
  return parseCategory(await apiRequest<unknown>(`/admin/categories/${encodeURIComponent(id)}/status`, {
    method: 'PATCH', body: { status },
  }));
}

export async function createProduct(input: ProductInput): Promise<AdminProduct> {
  return parseProduct(await apiRequest<unknown>('/admin/products', { method: 'POST', body: input }));
}

export async function updateProduct(id: string, input: ProductInput): Promise<AdminProduct> {
  return parseProduct(await apiRequest<unknown>(`/admin/products/${encodeURIComponent(id)}`, {
    method: 'PUT', body: input,
  }));
}

export async function changeProductStatus(id: string, status: CatalogStatus): Promise<AdminProduct> {
  return parseProduct(await apiRequest<unknown>(`/admin/products/${encodeURIComponent(id)}/status`, {
    method: 'PATCH', body: { status },
  }));
}

function parsePage<T>(value: unknown, parseItem: (item: unknown) => T): AdminPage<T> {
  const source = record(value);
  const page = record(source.page);
  if (!Array.isArray(source.items)) invalid();
  return {
    items: source.items.map(parseItem),
    page: {
      number: integer(page.number, 0), size: integer(page.size, 1),
      totalItems: integer(page.totalItems, 0), totalPages: integer(page.totalPages, 0),
      hasNext: bool(page.hasNext), hasPrevious: bool(page.hasPrevious),
    },
  };
}

function parseCategory(value: unknown): AdminCategory {
  const source = record(value);
  return {
    id: uuid(source.id), name: text(source.name), slug: slug(source.slug),
    displayOrder: integer(source.displayOrder, 0), status: status(source.status),
    createdAt: instant(source.createdAt), updatedAt: instant(source.updatedAt), version: integer(source.version, 0),
  };
}

function parseProduct(value: unknown): AdminProduct {
  const source = record(value);
  const category = record(source.category);
  return {
    id: uuid(source.id),
    category: { id: uuid(category.id), name: text(category.name), slug: slug(category.slug) },
    name: text(source.name), slug: slug(source.slug), description: text(source.description),
    priceVnd: integer(source.priceVnd, 1), status: status(source.status), imageUrl: nullableUrl(source.imageUrl),
    createdAt: instant(source.createdAt), updatedAt: instant(source.updatedAt), version: integer(source.version, 0),
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
  if (!/^[0-9a-f]{8}-(?:[0-9a-f]{4}-){3}[0-9a-f]{12}$/i.test(parsed)) invalid();
  return parsed;
}
function slug(value: unknown): string {
  const parsed = text(value);
  if (!/^[a-z0-9]+(?:-[a-z0-9]+)*$/.test(parsed)) invalid();
  return parsed;
}
function integer(value: unknown, minimum: number): number {
  if (typeof value !== 'number' || !Number.isSafeInteger(value) || value < minimum) invalid();
  return value;
}
function bool(value: unknown): boolean {
  if (typeof value !== 'boolean') invalid();
  return value;
}
function status(value: unknown): CatalogStatus {
  if (value !== 'ACTIVE' && value !== 'INACTIVE') invalid();
  return value;
}
function instant(value: unknown): string {
  const parsed = text(value);
  if (Number.isNaN(Date.parse(parsed))) invalid();
  return parsed;
}
function nullableUrl(value: unknown): string | null {
  if (value === null) return null;
  const parsed = text(value);
  try {
    const url = new URL(parsed);
    if (url.protocol !== 'https:' || url.username || url.password) invalid();
  } catch {
    invalid();
  }
  return parsed;
}
function invalid(): never {
  throw new TypeError('Invalid admin catalog response');
}
