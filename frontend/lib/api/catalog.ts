import { ApiError, readProblem } from './errors';
import {
  type CatalogPage,
  type CatalogProductDetail,
  type CatalogQueryInput,
  parseCatalogPage,
  parseProductDetail,
} from '@/lib/contracts/catalog';

const PRODUCT_SLUG_PATTERN = /^[a-z0-9]+(?:-[a-z0-9]+)*$/;

export async function listProducts(query: CatalogQueryInput = {}): Promise<CatalogPage> {
  assertServerOnly();
  const url = apiUrl('/api/v1/products');
  setTextParam(url, 'q', query.q);
  setTextParam(url, 'category', query.category);
  setTextParam(url, 'availability', query.availability);
  setTextParam(url, 'sort', query.sort);
  setIntegerParam(url, 'page', query.page, 0);
  setIntegerParam(url, 'size', query.size, 1);
  return requestCatalog(url, parseCatalogPage);
}

export async function getProduct(slug: string): Promise<CatalogProductDetail> {
  assertServerOnly();
  if (!PRODUCT_SLUG_PATTERN.test(slug)) {
    throw new ApiError(400, {
      code: 'INVALID_PRODUCT_SLUG',
      detail: 'Product slug is invalid.',
    });
  }
  return requestCatalog(apiUrl(`/api/v1/products/${encodeURIComponent(slug)}`), parseProductDetail);
}

function assertServerOnly(): void {
  if (typeof window !== 'undefined') {
    throw new Error('Catalog backend adapter is server-only');
  }
}

async function requestCatalog<T>(url: URL, parse: (value: unknown) => T): Promise<T> {
  let response: Response;
  try {
    response = await fetch(url, {
      headers: { Accept: 'application/json' },
      cache: 'no-store',
    });
  } catch {
    throw new ApiError(502, {
      code: 'UPSTREAM_UNAVAILABLE',
      detail: 'The catalog service is temporarily unavailable.',
    });
  }

  if (!response.ok) {
    throw new ApiError(response.status, await readProblem(response));
  }

  try {
    return parse((await response.json()) as unknown);
  } catch {
    throw new ApiError(502, {
      code: 'INVALID_UPSTREAM_RESPONSE',
      detail: 'The catalog service returned an invalid response.',
    });
  }
}

function apiUrl(path: string): URL {
  const origin = new URL(process.env.BACKEND_ORIGIN ?? 'http://localhost:8080');
  if (!['http:', 'https:'].includes(origin.protocol) || origin.username || origin.password || origin.pathname !== '/') {
    throw new Error('BACKEND_ORIGIN must be an HTTP(S) origin without credentials or a path');
  }
  return new URL(path, origin);
}

function setTextParam(url: URL, name: string, value: string | undefined): void {
  if (value !== undefined && value.trim() !== '') url.searchParams.set(name, value.trim());
}

function setIntegerParam(url: URL, name: string, value: number | undefined, minimum: number): void {
  if (value === undefined) return;
  if (!Number.isSafeInteger(value) || value < minimum) {
    throw new ApiError(400, { code: 'INVALID_CATALOG_QUERY', detail: 'Catalog query is invalid.' });
  }
  url.searchParams.set(name, String(value));
}
