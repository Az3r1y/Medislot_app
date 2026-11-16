import { afterAll, beforeAll, beforeEach, expect, test, vi } from 'vitest';
import {
  InMemorySpanExporter,
  SimpleSpanProcessor,
  type TracerProvider,
} from '@opentelemetry/sdk-trace';

const exporter = new InMemorySpanExporter();
const fetchMock = vi.fn(async (input: RequestInfo | URL, _init?: RequestInit) => {
  const r = new Response('[]', { status: 200 });
  Object.defineProperty(r, 'url', { value: String(input) }); // comme un vrai fetch
  return r;
});
let provider: TracerProvider;

beforeAll(async () => {
  globalThis.fetch = fetchMock as typeof fetch;
  vi.stubGlobal('location', new URL('http://localhost/')); // /api est sur la même origine que la page
  const { demarrerTraces } = await import('./tracing');
  provider = demarrerTraces({ processeur: new SimpleSpanProcessor({ exporter }) });
});
afterAll(() => provider.shutdown());
beforeEach(() => {
  exporter.reset();
  fetchMock.mockClear();
});

const waitForSpans = () =>
  vi.waitFor(() => {
    if (!exporter.getFinishedSpans().length) throw new Error('aucun span');
  });

test('un appel /api part avec un en-tête traceparent du span medislot-web', async () => {
  await fetch('http://localhost/api/professionals');
  await waitForSpans();
  const [input, init] = fetchMock.mock.calls[0];
  const headers = input instanceof Request ? input.headers : new Headers(init?.headers);
  const span = exporter.getFinishedSpans()[0];
  expect(headers.get('traceparent')).toMatch(
    new RegExp(`^00-${span.spanContext().traceId}-[0-9a-f]{16}-01$`),
  );
  expect(span.resource.attributes['service.name']).toBe('medislot-web');
});

test("l'export /otlp/ n'est pas lui-même tracé", async () => {
  await fetch('http://localhost/otlp/v1/traces', { method: 'POST' });
  await new Promise((r) => setTimeout(r, 50));
  expect(exporter.getFinishedSpans()).toHaveLength(0);
});

test('la saisie de recherche ne reste pas dans url.full', async () => {
  await fetch('http://localhost/api/professionals?q=cardiologue%20Dupont');
  await waitForSpans();
  const attrs = exporter.getFinishedSpans()[0].attributes;
  expect(JSON.stringify(attrs)).not.toContain('Dupont');
  expect(attrs['url.full'] ?? attrs['http.url']).toBe('http://localhost/api/professionals');
});
