import { ApiError, apiRequest } from './client';

export type DepositChannel = 'BANK' | 'MOMO';
export type CreateDepositChannel = 'MOMO';
export type DepositStatus = 'PENDING' | 'PAID' | 'FAILED' | 'EXPIRED' | 'REVIEW';

export type PaymentInstructions = {
  redirectUrl?: string;
  qrPayload?: string;
};

export type Deposit = {
  id: string;
  channel: DepositChannel;
  providerCode: string;
  merchantReference: string;
  amountVnd: number;
  currency: 'VND';
  status: DepositStatus;
  expiresAt: string;
  paidAt: string | null;
  instructions: PaymentInstructions | null;
};

const UUID_PATTERN = /^[0-9a-f]{8}-(?:[0-9a-f]{4}-){3}[0-9a-f]{12}$/i;
const PROVIDER_CODE_PATTERN = /^[A-Z0-9_]{2,40}$/;
const REFERENCE_PATTERN = /^[A-Za-z0-9_-]{8,100}$/;
const CHANNELS = new Set<DepositChannel>(['BANK', 'MOMO']);
const CREATE_CHANNELS = new Set<CreateDepositChannel>(['MOMO']);
const STATUSES = new Set<DepositStatus>(['PENDING', 'PAID', 'FAILED', 'EXPIRED', 'REVIEW']);

export async function createDeposit(channel: CreateDepositChannel, amountVnd: number): Promise<Deposit> {
  if (!CREATE_CHANNELS.has(channel) || !Number.isSafeInteger(amountVnd) || amountVnd <= 0 || amountVnd > 1_000_000_000) {
    throw new TypeError('Invalid deposit request');
  }
  return parseDeposit(
    await apiRequest<unknown>('/deposits', { method: 'POST', body: { channel, amountVnd } }),
    true
  );
}

export async function getDeposit(depositId: string): Promise<Deposit> {
  if (!UUID_PATTERN.test(depositId)) throw new TypeError('Invalid deposit identifier');
  return parseDeposit(await apiRequest<unknown>(`/deposits/${encodeURIComponent(depositId)}`), false);
}

function parseDeposit(value: unknown, requireInstructions: boolean): Deposit {
  const source = record(value);
  const status = enumValue(source.status, STATUSES);
  const paidAt = source.paidAt == null ? null : instant(source.paidAt);
  if ((status === 'PAID') !== (paidAt !== null)) invalidResponse();

  return {
    id: patternedText(source.id, UUID_PATTERN),
    channel: enumValue(source.channel, CHANNELS),
    providerCode: patternedText(source.providerCode, PROVIDER_CODE_PATTERN),
    merchantReference: patternedText(source.merchantReference, REFERENCE_PATTERN),
    amountVnd: vnd(source.amountVnd),
    currency: source.currency === 'VND' ? 'VND' : invalidResponse(),
    status,
    expiresAt: instant(source.expiresAt),
    paidAt,
    instructions: parseInstructions(source.instructions, requireInstructions),
  };
}

function parseInstructions(value: unknown, required: boolean): PaymentInstructions | null {
  if (value == null) return required ? invalidResponse() : null;
  const source = record(value);
  const redirectUrl = source.redirectUrl == null ? undefined : httpsUrl(source.redirectUrl);
  const qrPayload = source.qrPayload == null ? undefined : boundedText(source.qrPayload, 4_096);
  if ((redirectUrl ? 1 : 0) + (qrPayload ? 1 : 0) !== 1) invalidResponse();
  return { redirectUrl, qrPayload };
}

function record(value: unknown): Record<string, unknown> {
  if (!value || typeof value !== 'object' || Array.isArray(value)) invalidResponse();
  return value as Record<string, unknown>;
}

function enumValue<T extends string>(value: unknown, allowed: Set<T>): T {
  if (typeof value !== 'string' || !allowed.has(value as T)) invalidResponse();
  return value as T;
}

function patternedText(value: unknown, pattern: RegExp): string {
  if (typeof value !== 'string' || !pattern.test(value)) invalidResponse();
  return value;
}

function boundedText(value: unknown, maximum: number): string {
  if (typeof value !== 'string' || value.trim() !== value || value.length === 0 || value.length > maximum) {
    invalidResponse();
  }
  return value;
}

function vnd(value: unknown): number {
  if (!Number.isSafeInteger(value) || (value as number) <= 0 || (value as number) > 1_000_000_000) invalidResponse();
  return value as number;
}

function instant(value: unknown): string {
  if (typeof value !== 'string' || Number.isNaN(Date.parse(value))) invalidResponse();
  return value;
}

function httpsUrl(value: unknown): string {
  if (typeof value !== 'string' || value.length > 2_048) invalidResponse();
  try {
    const parsed = new URL(value);
    if (parsed.protocol !== 'https:' || parsed.username || parsed.password) invalidResponse();
    return parsed.toString();
  } catch {
    return invalidResponse();
  }
}

function invalidResponse(): never {
  throw new ApiError(0, { code: 'DEPOSIT_RESPONSE_INVALID', detail: 'Deposit response is invalid.' });
}
