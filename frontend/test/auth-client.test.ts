import { beforeEach, describe, expect, it, vi } from 'vitest';

import { getCurrentUser, login, logout, restoreSession, SESSION_ENDED_EVENT } from '@/lib/api/auth';

const user = {
  id: 7,
  username: 'buyer',
  firstName: 'Test',
  lastName: 'Buyer',
  roles: ['ROLE_USER'],
};

describe('auth API client', () => {
  beforeEach(() => {
    document.cookie = 'XSRF-TOKEN=; Max-Age=0; Path=/';
    vi.unstubAllGlobals();
  });

  it('obtains CSRF and sends browser credentials without Web Storage', async () => {
    const localStorageSpy = vi.spyOn(window.localStorage, 'setItem');
    const sessionStorageSpy = vi.spyOn(window.sessionStorage, 'setItem');
    let sentRequest: RequestInit | undefined;
    const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      if (String(input).endsWith('/auth/csrf')) {
        document.cookie = 'XSRF-TOKEN=csrf-value; Path=/';
        return new Response(null, { status: 204 });
      }
      sentRequest = init;
      return Response.json(user);
    });
    vi.stubGlobal('fetch', fetchMock);

    await login({ username: 'buyer', password: 'secure-password' });

    expect(fetchMock).toHaveBeenCalledTimes(2);
    expect(fetchMock.mock.calls[0]?.[0]).toBe('/api/v1/auth/csrf');
    expect(sentRequest?.credentials).toBe('include');
    expect(new Headers(sentRequest?.headers).get('X-XSRF-TOKEN')).toBe('csrf-value');
    expect(sentRequest?.body).toBe(JSON.stringify({ username: 'buyer', password: 'secure-password' }));
    expect(localStorageSpy).not.toHaveBeenCalled();
    expect(sessionStorageSpy).not.toHaveBeenCalled();
  });

  it('derives navigation state from me and uses refresh only after a 401', async () => {
    const fetchMock = vi.fn(async (input: RequestInfo | URL) => {
      const url = String(input);
      if (url.endsWith('/auth/me')) {
        return Response.json({ code: 'AUTHENTICATION_REQUIRED' }, { status: 401 });
      }
      if (url.endsWith('/auth/csrf')) {
        document.cookie = 'XSRF-TOKEN=refresh-csrf; Path=/';
        return new Response(null, { status: 204 });
      }
      if (url.endsWith('/auth/refresh')) {
        return Response.json(user);
      }
      throw new Error(`Unexpected request: ${url}`);
    });
    vi.stubGlobal('fetch', fetchMock);

    await expect(restoreSession()).resolves.toEqual(user);
    expect(fetchMock.mock.calls.map(([input]) => String(input))).toEqual([
      '/api/v1/auth/me',
      '/api/v1/auth/csrf',
      '/api/v1/auth/refresh',
    ]);
  });

  it('coalesces concurrent session restores into one refresh rotation', async () => {
    const fetchMock = vi.fn(async (input: RequestInfo | URL) => {
      const url = String(input);
      if (url.endsWith('/auth/me')) {
        return Response.json({ code: 'AUTHENTICATION_REQUIRED' }, { status: 401 });
      }
      if (url.endsWith('/auth/csrf')) {
        document.cookie = 'XSRF-TOKEN=single-flight-csrf; Path=/';
        return new Response(null, { status: 204 });
      }
      if (url.endsWith('/auth/refresh')) {
        return Response.json(user);
      }
      throw new Error(`Unexpected request: ${url}`);
    });
    vi.stubGlobal('fetch', fetchMock);

    await expect(Promise.all([restoreSession(), restoreSession()])).resolves.toEqual([user, user]);
    expect(fetchMock.mock.calls.map(([input]) => String(input))).toEqual([
      '/api/v1/auth/me',
      '/api/v1/auth/csrf',
      '/api/v1/auth/refresh',
    ]);
  });

  it('exposes structured problem details without leaking arbitrary response bodies', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () =>
        Response.json(
          {
            code: 'AUTHENTICATION_REQUIRED',
            detail: 'Authentication is required.',
            errors: { username: ['Invalid username'] },
          },
          { status: 401, headers: { 'Content-Type': 'application/problem+json' } }
        )
      )
    );

    await expect(getCurrentUser()).rejects.toMatchObject({
      status: 401,
      code: 'AUTHENTICATION_REQUIRED',
      fieldErrors: { username: ['Invalid username'] },
    });
  });

  it('announces session end so sensitive UI can clear even when logout fails', async () => {
    const listener = vi.fn();
    window.addEventListener(SESSION_ENDED_EVENT, listener);
    vi.stubGlobal(
      'fetch',
      vi.fn(async (input: RequestInfo | URL) => {
        if (String(input).endsWith('/auth/csrf')) {
          document.cookie = 'XSRF-TOKEN=logout-csrf; Path=/';
          return new Response(null, { status: 204 });
        }
        return Response.json({ code: 'LOGOUT_FAILED' }, { status: 503 });
      })
    );

    await expect(logout()).rejects.toMatchObject({ status: 503 });
    expect(listener).toHaveBeenCalledTimes(1);
    window.removeEventListener(SESSION_ENDED_EVENT, listener);
  });
});
