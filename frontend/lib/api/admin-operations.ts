import { apiRequest } from './client';

export type AdminOrderStatus = 'COMPLETED' | 'FAILED' | 'REFUNDED';
export type AdminDepositStatus = 'PENDING' | 'PAID' | 'FAILED' | 'EXPIRED' | 'REVIEW';
export type AdminDepositChannel = 'MOMO';

export type AdminOperationsPage<T> = {
  items: T[];
  page: {
    number: number;
    size: number;
    totalItems: number;
    totalPages: number;
    hasNext: boolean;
    hasPrevious: boolean;
  };
};

export type AdminOrder = {
  orderId: string;
  userId: number;
  status: AdminOrderStatus;
  totalVnd: number;
  createdAt: string;
  updatedAt: string;
};

export type AdminDeposit = {
  id: string;
  userId: number;
  channel: AdminDepositChannel;
  providerCode: string;
  amountVnd: number;
  currency: 'VND';
  status: AdminDepositStatus;
  expiresAt: string;
  paidAt: string | null;
  createdAt: string;
  updatedAt: string;
  needsReconciliationReview: boolean;
};

export type AdminOrderFilters = {
  page?: number;
  size?: number;
  status?: AdminOrderStatus;
  userId?: number;
};

export type AdminDepositFilters = {
  page?: number;
  size?: number;
  status?: AdminDepositStatus;
  channel?: AdminDepositChannel;
  userId?: number;
};

export async function listAdminOrders(filters: AdminOrderFilters = {}): Promise<AdminOperationsPage<AdminOrder>> {
  const query = buildQuery(filters, ['COMPLETED', 'FAILED', 'REFUNDED']);
  return parsePage(await apiRequest<unknown>(`/admin/orders?${query}`), parseOrder);
}

export async function listAdminDeposits(
  filters: AdminDepositFilters = {}
): Promise<AdminOperationsPage<AdminDeposit>> {
  const query = buildQuery(filters, ['PENDING', 'PAID', 'FAILED', 'EXPIRED', 'REVIEW']);
  return parsePage(await apiRequest<unknown>(`/admin/deposits?${query}`), parseDeposit);
}

function buildQuery(
  filters: { page?: number; size?: number; status?: string; channel?: string; userId?: number },
  allowedStatuses: readonly string[]
): string {
  const page = filters.page ?? 0;
  const size = filters.size ?? 20;
  assertInteger(page, 0);
  assertInteger(size, 1);
  if (size > 100) throw new TypeError('Invalid admin operations page request');
  if (filters.userId !== undefined) assertInteger(filters.userId, 1);
  if (filters.status && !allowedStatuses.includes(filters.status)) {
    throw new TypeError('Invalid admin operations filter');
  }
  if (filters.channel && filters.channel !== 'MOMO') throw new TypeError('Invalid admin operations filter');

  const query = new URLSearchParams({ page: String(page), size: String(size) });
  if (filters.status) query.set('status', filters.status);
  if (filters.channel) query.set('channel', filters.channel);
  if (filters.userId !== undefined) query.set('userId', String(filters.userId));
  return query.toString();
}

function parsePage<T>(value: unknown, parseItem: (item: unknown) => T): AdminOperationsPage<T> {
  const source = record(value);
  const page = record(source.page);
  if (!Array.isArray(source.items)) invalid();
  const parsedPage = {
    number: integer(page.number, 0),
    size: integer(page.size, 1),
    totalItems: integer(page.totalItems, 0),
    totalPages: integer(page.totalPages, 0),
    hasNext: bool(page.hasNext),
    hasPrevious: bool(page.hasPrevious),
  };
  if (parsedPage.totalPages > 0 && parsedPage.number >= parsedPage.totalPages) invalid();
  return { items: source.items.map(parseItem), page: parsedPage };
}

function parseOrder(value: unknown): AdminOrder {
  const source = record(value);
  return {
    orderId: uuid(source.orderId),
    userId: integer(source.userId, 1),
    status: oneOf(source.status, ['COMPLETED', 'FAILED', 'REFUNDED'] as const),
    totalVnd: integer(source.totalVnd, 1),
    createdAt: instant(source.createdAt),
    updatedAt: instant(source.updatedAt),
  };
}

function parseDeposit(value: unknown): AdminDeposit {
  const source = record(value);
  const currency = source.currency === 'VND' ? 'VND' : invalid();
  return {
    id: uuid(source.id),
    userId: integer(source.userId, 1),
    channel: oneOf(source.channel, ['MOMO'] as const),
    providerCode: providerCode(source.providerCode),
    amountVnd: integer(source.amountVnd, 1),
    currency,
    status: oneOf(source.status, ['PENDING', 'PAID', 'FAILED', 'EXPIRED', 'REVIEW'] as const),
    expiresAt: instant(source.expiresAt),
    paidAt: source.paidAt == null ? null : instant(source.paidAt),
    createdAt: instant(source.createdAt),
    updatedAt: instant(source.updatedAt),
    needsReconciliationReview: bool(source.needsReconciliationReview),
  };
}

function record(value: unknown): Record<string, unknown> {
  if (!value || typeof value !== 'object' || Array.isArray(value)) invalid();
  return value as Record<string, unknown>;
}

function uuid(value: unknown): string {
  if (typeof value !== 'string' || !/^[0-9a-f]{8}-(?:[0-9a-f]{4}-){3}[0-9a-f]{12}$/i.test(value)) invalid();
  return value;
}

function providerCode(value: unknown): string {
  if (typeof value !== 'string' || !/^[A-Z0-9_]{2,40}$/.test(value)) invalid();
  return value;
}

function integer(value: unknown, minimum: number): number {
  if (typeof value !== 'number' || !Number.isSafeInteger(value) || value < minimum) invalid();
  return value;
}

function assertInteger(value: number, minimum: number): void {
  if (!Number.isSafeInteger(value) || value < minimum) throw new TypeError('Invalid admin operations page request');
}

function instant(value: unknown): string {
  if (typeof value !== 'string' || Number.isNaN(Date.parse(value))) invalid();
  return value;
}

function bool(value: unknown): boolean {
  if (typeof value !== 'boolean') invalid();
  return value;
}

function oneOf<const T extends string>(value: unknown, allowed: readonly T[]): T {
  if (typeof value !== 'string' || !allowed.includes(value as T)) invalid();
  return value as T;
}

function invalid(): never {
  throw new TypeError('Invalid admin operations response');
}
