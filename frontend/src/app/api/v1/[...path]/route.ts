const backendOrigin = parseBackendOrigin(process.env.BACKEND_ORIGIN ?? 'http://localhost:8080');
const requestHeaders = ['accept', 'content-type', 'cookie', 'idempotency-key', 'x-request-id', 'x-xsrf-token'];
const responseHeaders = ['cache-control', 'clear-site-data', 'content-type', 'vary', 'x-request-id'];

type RouteContext = { params: Promise<{ path: string[] }> };

async function proxy(request: Request, context: RouteContext): Promise<Response> {
  const { path } = await context.params;
  const upstreamUrl = new URL(`/api/v1/${path.map(encodeURIComponent).join('/')}`, backendOrigin);
  upstreamUrl.search = new URL(request.url).search;

  const headers = new Headers();
  for (const name of requestHeaders) {
    const value = request.headers.get(name);
    if (value) headers.set(name, value);
  }

  try {
    const hasBody = request.method !== 'GET' && request.method !== 'HEAD';
    const upstream = await fetch(upstreamUrl, {
      method: request.method,
      headers,
      body: hasBody ? await request.arrayBuffer() : undefined,
      cache: 'no-store',
      redirect: 'manual',
    });
    const outgoingHeaders = new Headers();
    for (const name of responseHeaders) {
      const value = upstream.headers.get(name);
      if (value) outgoingHeaders.set(name, value);
    }
    for (const cookie of upstream.headers.getSetCookie()) {
      outgoingHeaders.append('set-cookie', cookie);
    }
    return new Response(upstream.body, {
      status: upstream.status,
      headers: outgoingHeaders,
    });
  } catch {
    return Response.json(
      {
        type: 'urn:problem:upstream-unavailable',
        title: 'Bad Gateway',
        status: 502,
        detail: 'The application service is temporarily unavailable.',
        code: 'UPSTREAM_UNAVAILABLE',
      },
      { status: 502, headers: { 'Cache-Control': 'no-store' } }
    );
  }
}

function parseBackendOrigin(value: string): URL {
  const url = new URL(value);
  if (!['http:', 'https:'].includes(url.protocol) || url.username || url.password || url.pathname !== '/') {
    throw new Error('BACKEND_ORIGIN must be an HTTP(S) origin without credentials or a path');
  }
  return url;
}

export const GET = proxy;
export const POST = proxy;
export const PUT = proxy;
export const PATCH = proxy;
export const DELETE = proxy;
export const OPTIONS = proxy;
