import { test, expect } from '@playwright/test';

// Le span du navigateur doit être le parent direct du span serveur, dans la même trace.
const JAEGER = process.env.JAEGER_URL || 'http://localhost:16686';
type JSpan = {
  spanID: string;
  operationName: string;
  processID: string;
  references?: { refType: string; spanID: string }[];
  tags: { key: string; value: unknown }[];
};
type JTrace = { spans: JSpan[]; processes: Record<string, { serviceName: string }> };

test('une recherche produit une trace front puis back', async ({ page, request }) => {
  await page.goto('/');
  await expect(page.getByText(/praticiens à découvrir/)).toBeVisible();

  const searchRequest = page.waitForRequest(
    (r) => r.url().includes('/api/professionals') && r.url().includes('q=Martin'),
  );
  await page.getByLabel('Nom du praticien').fill('Martin');
  await page.getByLabel('Nom du praticien').press('Enter');
  const traceparent = (await searchRequest).headers()['traceparent'];
  expect(traceparent, 'traceparent absent').toMatch(/^00-[0-9a-f]{32}-[0-9a-f]{16}-01$/);
  const [, traceId, browserSpanId] = traceparent.split('-');

  // La page reste ouverte : l'export par lots part toutes les 5 s environ.
  let trace: JTrace | undefined;
  await expect
    .poll(
      async () => {
        const r = await request.get(`${JAEGER}/api/traces/${traceId}`);
        if (!r.ok()) return 'trace absente de Jaeger';
        trace = (await r.json()).data[0];
        const services = new Set(
          trace!.spans.map((s) => trace!.processes[s.processID].serviceName),
        );
        return services.has('medislot-web') && services.has('medislot-api')
          ? 'ok'
          : [...services].join(',');
      },
      { timeout: 30_000, intervals: [1_000] },
    )
    .toBe('ok');

  const service = (s: JSpan) => trace!.processes[s.processID].serviceName;
  const front = trace!.spans.find((s) => s.spanID === browserSpanId)!;
  expect(service(front)).toBe('medislot-web');
  expect(front.operationName).toBe('GET');

  const back = trace!.spans.find(
    (s) =>
      service(s) === 'medislot-api' &&
      s.references?.some((r) => r.refType === 'CHILD_OF' && r.spanID === browserSpanId),
  );
  expect(back, 'aucun span du back n’a le span navigateur pour parent').toBeTruthy();
  expect(back!.operationName).toBe('GET /api/professionals');

  console.log(
    `trace ${traceId} : ${front.operationName} (medislot-web) -> ${back!.operationName} (medislot-api)`,
  );
  // La saisie ne doit apparaître dans aucun span, front ou back.
  expect(JSON.stringify(trace!.spans.map((s) => s.tags))).not.toContain('Martin');
});
