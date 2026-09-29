import { ApiError, readProblem } from './errors';

export { ApiError } from './errors';
export type { ProblemDetails } from './errors';

type JsonRequestOptions = Omit<RequestInit, 'body'> & {
  body?: unknown;
};

const API_PREFIX = '/api/v1';
const CSRF_COOKIE = 'XSRF-TOKEN';
const CSRF_HEADER = 'X-XSRF-TOKEN';
const SAFE_METHODS = new Set(['GET', 'HEAD', 'OPTIONS']);

export async function apiRequest<T>(path: string, options: JsonRequestOptions = {}): Promise<T> {
  const method = (options.method ?? 'GET').toUpperCase();
  const headers = new Headers(options.headers);

  if (!SAFE_METHODS.has(method)) {
    await issueCsrfCookie();
    const csrfToken = readCookie(CSRF_COOKIE);
    if (!csrfToken) {
      throw new ApiError(0, { code: 'CSRF_UNAVAILABLE', detail: 'Unable to initialize a secure request.' });
    }
    headers.set(CSRF_HEADER, csrfToken);
  }

  if (options.body !== undefined) {
    headers.set('Content-Type', 'application/json');
  }
  headers.set('Accept', 'application/json');

  const response = await fetch(`${API_PREFIX}${path}`, {
    ...options,
    method,
    headers,
    credentials: 'include',
    cache: 'no-store',
    body: options.body === undefined ? undefined : JSON.stringify(options.body),
  });

  if (!response.ok) {
    throw new ApiError(response.status, await readProblem(response));
  }
  if (response.status === 204) {
    return undefined as T;
  }
  return (await response.json()) as T;
}

async function issueCsrfCookie(): Promise<void> {
  const response = await fetch(`${API_PREFIX}/auth/csrf`, {
    method: 'GET',
    headers: { Accept: 'application/json' },
    credentials: 'include',
    cache: 'no-store',
  });
  if (!response.ok) {
    throw new ApiError(response.status, await readProblem(response));
  }
}

function readCookie(name: string): string | undefined {
  if (typeof document === 'undefined') return undefined;
  const prefix = `${encodeURIComponent(name)}=`;
  const pair = document.cookie
    .split(';')
    .map((value) => value.trim())
    .find((value) => value.startsWith(prefix));
  return pair ? decodeURIComponent(pair.slice(prefix.length)) : undefined;
}
