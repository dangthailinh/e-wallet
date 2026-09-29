import { apiRequest } from './client';
import { parseCatalogPage, type CatalogProductSummary } from '@/lib/contracts/catalog';

export type InventoryStatus = 'AVAILABLE' | 'RESERVED' | 'SOLD' | 'DISABLED';

export type AdminInventoryItem = {
  id: string;
  productId: string;
  status: InventoryStatus;
  checksumSuffix: string;
  keyVersion: number;
  createdAt: string;
  updatedAt: string;
};

export type AdminInventoryList = {
  productId: string;
  counts: Record<InventoryStatus, number>;
  items: AdminInventoryItem[];
};

export type InventoryImportResult = {
  row: number;
  status: 'IMPORTED' | 'REJECTED';
  inventoryItemId: string | null;
  code: string | null;
  detail: string | null;
};

export type InventoryImportResponse = {
  requested: number;
  imported: number;
  results: InventoryImportResult[];
};

export async function listAdminProducts(): Promise<CatalogProductSummary[]> {
  return parseCatalogPage(await apiRequest<unknown>('/products?sort=name-asc&size=100')).items;
}

export async function listInventory(productId: string): Promise<AdminInventoryList> {
  return parseInventoryList(await apiRequest<unknown>(`/admin/products/${encodeURIComponent(productId)}/inventory-items`));
}

export async function importInventory(productId: string, secrets: string[]): Promise<InventoryImportResponse> {
  const value = await apiRequest<unknown>(`/admin/products/${encodeURIComponent(productId)}/inventory-items`, {
    method: 'POST',
    body: { secrets },
  });
  return parseImportResponse(value);
}

export async function changeInventoryStatus(
  itemId: string,
  status: 'AVAILABLE' | 'DISABLED'
): Promise<AdminInventoryItem> {
  const value = await apiRequest<unknown>(`/admin/inventory-items/${encodeURIComponent(itemId)}/status`, {
    method: 'PATCH',
    body: { status },
  });
  return parseItem(value);
}

function parseInventoryList(value: unknown): AdminInventoryList {
  const source = record(value);
  const counts = record(source.counts);
  if (!Array.isArray(source.items)) invalid();
  return {
    productId: uuid(source.productId),
    counts: {
      AVAILABLE: integer(counts.AVAILABLE, 0),
      RESERVED: integer(counts.RESERVED, 0),
      SOLD: integer(counts.SOLD, 0),
      DISABLED: integer(counts.DISABLED, 0),
    },
    items: source.items.map(parseItem),
  };
}

function parseImportResponse(value: unknown): InventoryImportResponse {
  const source = record(value);
  if (!Array.isArray(source.results)) invalid();
  return {
    requested: integer(source.requested, 0),
    imported: integer(source.imported, 0),
    results: source.results.map((item) => {
      const row = record(item);
      const status = oneOf(row.status, ['IMPORTED', 'REJECTED'] as const);
      return {
        row: integer(row.row, 1),
        status,
        inventoryItemId: nullable(row.inventoryItemId, uuid),
        code: nullable(row.code, text),
        detail: nullable(row.detail, text),
      };
    }),
  };
}

function parseItem(value: unknown): AdminInventoryItem {
  const source = record(value);
  const checksumSuffix = text(source.checksumSuffix);
  if (!/^[0-9a-f]{6}$/.test(checksumSuffix)) invalid();
  return {
    id: uuid(source.id),
    productId: uuid(source.productId),
    status: oneOf(source.status, ['AVAILABLE', 'RESERVED', 'SOLD', 'DISABLED'] as const),
    checksumSuffix,
    keyVersion: integer(source.keyVersion, 1),
    createdAt: instant(source.createdAt),
    updatedAt: instant(source.updatedAt),
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
  if (!/^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(parsed)) invalid();
  return parsed;
}

function integer(value: unknown, minimum: number): number {
  if (typeof value !== 'number' || !Number.isSafeInteger(value) || value < minimum) invalid();
  return value;
}

function instant(value: unknown): string {
  const parsed = text(value);
  if (!/^\d{4}-\d{2}-\d{2}T/.test(parsed) || Number.isNaN(Date.parse(parsed))) invalid();
  return parsed;
}

function nullable<T>(value: unknown, parse: (candidate: unknown) => T): T | null {
  return value === null ? null : parse(value);
}

function oneOf<const T extends string>(value: unknown, allowed: readonly T[]): T {
  if (typeof value !== 'string' || !allowed.includes(value as T)) invalid();
  return value as T;
}

function invalid(): never {
  throw new TypeError('Invalid admin inventory response');
}
