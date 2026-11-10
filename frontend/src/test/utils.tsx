import { render } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter } from 'react-router-dom';
import type { ReactElement } from 'react';

type Handler = (url: string, init: RequestInit) => { status: number; body?: unknown };

/** Installs a fetch mock routed by "METHOD path" prefix. Returns the recorded calls. */
export function mockFetch(routes: Record<string, Handler | { status: number; body?: unknown }>) {
  const calls: { method: string; url: string; init: RequestInit }[] = [];
  globalThis.fetch = vi.fn(async (input: RequestInfo | URL, init: RequestInit = {}) => {
    const url = String(input);
    const method = (init.method ?? 'GET').toUpperCase();
    calls.push({ method, url, init });
    const key = Object.keys(routes)
      .filter((k) => {
        const [m, p] = k.split(' ');
        return m === method && url.startsWith(p);
      })
      .sort((a, b) => b.length - a.length)[0];
    if (!key) return new Response(JSON.stringify({ code: 'NOT_MOCKED', message: `${method} ${url}` }), { status: 404 });
    const r = routes[key];
    const res = typeof r === 'function' ? r(url, init) : r;
    return new Response(res.body === undefined ? null : JSON.stringify(res.body), { status: res.status });
  }) as typeof fetch;
  return calls;
}

export function renderApp(ui: ReactElement, path = '/') {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={qc}>
      <MemoryRouter initialEntries={[path]}>{ui}</MemoryRouter>
    </QueryClientProvider>,
  );
}
