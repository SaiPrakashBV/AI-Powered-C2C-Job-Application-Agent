/** Typed fetch wrapper: same-origin session cookie, CSRF double-submit header, consistent ApiError. */

export interface FieldError { field: string; message: string }

export class ApiError extends Error {
  constructor(
    public status: number,
    public code: string,
    message: string,
    public fieldErrors: FieldError[] = [],
  ) {
    super(message);
  }
}

function readCookie(name: string): string | undefined {
  return document.cookie
    .split('; ')
    .find((c) => c.startsWith(name + '='))
    ?.split('=')[1];
}

let csrfPrimed = false;

async function ensureCsrfCookie() {
  if (csrfPrimed && readCookie('XSRF-TOKEN')) return;
  await fetch('/api/v1/auth/csrf', { credentials: 'same-origin' });
  csrfPrimed = true;
}

export async function api<T>(path: string, init: RequestInit & { json?: unknown } = {}): Promise<T> {
  const method = (init.method ?? 'GET').toUpperCase();
  const headers = new Headers(init.headers);
  headers.set('Accept', 'application/json');
  let body = init.body;
  if (init.json !== undefined) {
    headers.set('Content-Type', 'application/json');
    body = JSON.stringify(init.json);
  }
  if (method !== 'GET' && method !== 'HEAD') {
    await ensureCsrfCookie();
    const token = readCookie('XSRF-TOKEN');
    if (token) headers.set('X-XSRF-TOKEN', decodeURIComponent(token));
  }
  let res: Response;
  try {
    res = await fetch(path, { ...init, method, headers, body, credentials: 'same-origin' });
  } catch {
    throw new ApiError(0, 'NETWORK_ERROR', 'Cannot reach the application server. Is the backend running?');
  }
  if (res.status === 204) return undefined as T;
  const text = await res.text();
  const data = text ? safeJson(text) : undefined;
  if (!res.ok) {
    const d = (data ?? {}) as { code?: string; message?: string; fieldErrors?: FieldError[] };
    if (res.status === 403) csrfPrimed = false; // token may have rotated (e.g. after login); refetch next time
    throw new ApiError(res.status, d.code ?? 'HTTP_' + res.status, d.message ?? res.statusText, d.fieldErrors ?? []);
  }
  return data as T;
}

function safeJson(text: string): unknown {
  try {
    return JSON.parse(text);
  } catch {
    return undefined;
  }
}

export function resetCsrf() {
  csrfPrimed = false;
}
