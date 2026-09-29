import { ApiError, apiRequest } from './client';

export type CheckoutOrder = {
  orderId: string;
  itemId: string;
  productId: string;
  productName: string;
  totalVnd: number;
  status: 'COMPLETED';
  createdAt: string;
  walletBalanceVnd: number;
};

export type OrderSummary = {
  orderId: string;
  status: 'COMPLETED';
  totalVnd: number;
  createdAt: string;
};

export type OrderItem = {
  itemId: string;
  productId: string;
  productName: string;
  unitPriceVnd: number;
};

export type OrderDetail = OrderSummary & { items: OrderItem[] };

export type OrderList = {
  items: OrderSummary[];
  page: number;
  size: number;
  totalItems: number;
  totalPages: number;
};

export type RevealedOrderItem = { itemId: string; secret: string };

const UUID_PATTERN = /^[0-9a-f]{8}-(?:[0-9a-f]{4}-){3}[0-9a-f]{12}$/i;
const IDEMPOTENCY_KEY_PATTERN = /^[A-Za-z0-9_-]{8,100}$/;

export async function checkoutProduct(
  productId: string,
  expectedPriceVnd: number,
  idempotencyKey: string
): Promise<CheckoutOrder> {
  if (
    !UUID_PATTERN.test(productId) ||
    !Number.isSafeInteger(expectedPriceVnd) ||
    expectedPriceVnd <= 0 ||
    !IDEMPOTENCY_KEY_PATTERN.test(idempotencyKey)
  ) {
    throw new TypeError('Invalid checkout request');
  }
  const value = await apiRequest<unknown>('/orders', {
    method: 'POST',
    headers: { 'Idempotency-Key': idempotencyKey },
    body: { productId, expectedPriceVnd },
  });
  return parseCheckoutOrder(value);
}

export async function listOrders(page = 0, size = 20): Promise<OrderList> {
  if (!Number.isSafeInteger(page) || page < 0 || !Number.isSafeInteger(size) || size < 1 || size > 100) {
    throw new TypeError('Invalid order page request');
  }
  const value = await apiRequest<unknown>(`/orders?page=${page}&size=${size}`);
  const source = record(value);
  if (!Array.isArray(source.items)) invalidResponse();
  const totalPages = integer(source.totalPages, 0);
  const parsedPage = integer(source.page, 0);
  if (totalPages > 0 && parsedPage >= totalPages) invalidResponse();
  return {
    items: source.items.map(parseOrderSummary),
    page: parsedPage,
    size: integer(source.size, 1),
    totalItems: integer(source.totalItems, 0),
    totalPages,
  };
}

export async function getOrder(orderId: string): Promise<OrderDetail> {
  assertUuid(orderId);
  const source = record(await apiRequest<unknown>(`/orders/${encodeURIComponent(orderId)}`));
  if (!Array.isArray(source.items)) invalidResponse();
  return { ...parseOrderSummary(source), items: source.items.map(parseOrderItem) };
}

export async function revealOrderItem(orderId: string, itemId: string): Promise<RevealedOrderItem> {
  assertUuid(orderId);
  assertUuid(itemId);
  const source = record(
    await apiRequest<unknown>(`/orders/${encodeURIComponent(orderId)}/items/${encodeURIComponent(itemId)}/reveal`, {
      method: 'POST',
    })
  );
  const responseItemId = uuid(source.itemId);
  if (responseItemId !== itemId) invalidResponse();
  return { itemId: responseItemId, secret: text(source.secret) };
}

function parseCheckoutOrder(value: unknown): CheckoutOrder {
  const source = record(value);
  return {
    orderId: uuid(source.orderId),
    itemId: uuid(source.itemId),
    productId: uuid(source.productId),
    productName: text(source.productName),
    totalVnd: vnd(source.totalVnd, false),
    status: completed(source.status),
    createdAt: instant(source.createdAt),
    walletBalanceVnd: vnd(source.walletBalanceVnd, true),
  };
}

function parseOrderSummary(value: unknown): OrderSummary {
  const source = record(value);
  return {
    orderId: uuid(source.orderId),
    status: completed(source.status),
    totalVnd: vnd(source.totalVnd, false),
    createdAt: instant(source.createdAt),
  };
}

function parseOrderItem(value: unknown): OrderItem {
  const source = record(value);
  return {
    itemId: uuid(source.itemId),
    productId: uuid(source.productId),
    productName: text(source.productName),
    unitPriceVnd: vnd(source.unitPriceVnd, false),
  };
}

function record(value: unknown): Record<string, unknown> {
  if (!value || typeof value !== 'object' || Array.isArray(value)) invalidResponse();
  return value as Record<string, unknown>;
}

function integer(value: unknown, minimum: number): number {
  if (!Number.isSafeInteger(value) || (value as number) < minimum) invalidResponse();
  return value as number;
}

function assertUuid(value: string): void {
  if (!UUID_PATTERN.test(value)) throw new TypeError('Invalid order identifier');
}

function uuid(value: unknown): string {
  if (typeof value !== 'string' || !UUID_PATTERN.test(value)) invalidResponse();
  return value;
}

function text(value: unknown): string {
  if (typeof value !== 'string' || value.trim() === '') invalidResponse();
  return value;
}

function vnd(value: unknown, allowZero: boolean): number {
  if (!Number.isSafeInteger(value) || (value as number) < (allowZero ? 0 : 1)) invalidResponse();
  return value as number;
}

function completed(value: unknown): 'COMPLETED' {
  if (value !== 'COMPLETED') invalidResponse();
  return value;
}

function instant(value: unknown): string {
  if (typeof value !== 'string' || Number.isNaN(Date.parse(value))) invalidResponse();
  return value;
}

function invalidResponse(): never {
  throw new ApiError(0, { code: 'ORDER_RESPONSE_INVALID', detail: 'Checkout response is invalid.' });
}
