import { afterEach, describe, expect, test, vi } from 'vitest';
import {
  FALLBACK,
  MESSAGES,
  OFFLINE,
  POPUP,
  fetchConvocation,
  filenameFrom,
  openConvocation,
  saveConvocation,
  type Browser,
} from './convocation';

const pdf = (disposition = 'inline; filename="convocation-12.pdf"') =>
  new Response(new Blob(['%PDF-1.7']), {
    status: 200,
    headers: { 'Content-Type': 'application/pdf', 'Content-Disposition': disposition },
  });
const problem = (status: number) =>
  new Response(JSON.stringify({ status, detail: 'détail serveur' }), {
    status,
    headers: { 'Content-Type': 'application/problem+json' },
  });
const respondWith = (r: Response | Error) =>
  vi.stubGlobal(
    'fetch',
    vi.fn(async () => {
      if (r instanceof Error) throw r;
      return r;
    }),
  );
afterEach(() => vi.unstubAllGlobals());

function fakeBrowser(withTab = true) {
  const tab = { show: vi.fn(), close: vi.fn() };
  const browser = {
    openTab: vi.fn<Browser['openTab']>(() => (withTab ? tab : null)),
    save: vi.fn<Browser['save']>(),
    later: vi.fn<Browser['later']>(),
    objectUrl: vi.fn<Browser['objectUrl']>(() => 'blob:convocation'),
    revoke: vi.fn<Browser['revoke']>(),
  } satisfies Browser;
  return { tab, browser };
}

describe('Contrat', () => {
  test('lit le nom proposé par le serveur', () => {
    expect(filenameFrom('attachment; filename="convocation-12.pdf"', 12)).toBe(
      'convocation-12.pdf',
    );
    expect(filenameFrom('inline; filename=convocation-12.pdf', 12)).toBe('convocation-12.pdf');
  });
  test('sans en-tête, un nom neutre avec le numéro seul', () => {
    expect(filenameFrom(null, 7)).toBe('convocation-7.pdf');
  });
  test('appelle la bonne URL, même origine, en attendant un PDF', async () => {
    respondWith(pdf());
    await fetchConvocation(12, true);
    expect(fetch).toHaveBeenCalledWith('/api/appointments/12/convocation.pdf?download=true', {
      credentials: 'same-origin',
      headers: { Accept: 'application/pdf' },
    });
  });
});

describe('Récupération du PDF', () => {
  test('200 PDF : le blob et le nom du serveur', async () => {
    respondWith(pdf());
    const r = await fetchConvocation(12);
    expect(r.filename).toBe('convocation-12.pdf');
    expect(await r.blob.text()).toBe('%PDF-1.7');
  });
  test.each([401, 404, 409, 500])('%i : une phrase, jamais le JSON', async (status) => {
    respondWith(problem(status));
    await expect(fetchConvocation(12)).rejects.toMatchObject({ message: MESSAGES[status], status });
  });
  test('code inattendu : message de repli', async () => {
    respondWith(problem(503));
    await expect(fetchConvocation(12)).rejects.toThrow(FALLBACK);
  });
  test('refuse une réponse 200 qui n’est pas un PDF', async () => {
    // Repli de Nginx vers index.html, ou portail captif.
    respondWith(
      new Response('<!doctype html><title>MediSlot</title>', {
        status: 200,
        headers: { 'Content-Type': 'text/html' },
      }),
    );
    await expect(fetchConvocation(12)).rejects.toThrow(FALLBACK);
  });
  test('réseau coupé : message hors ligne', async () => {
    respondWith(new TypeError('Failed to fetch'));
    await expect(fetchConvocation(12)).rejects.toThrow(OFFLINE);
  });
});

describe('Affichage et mémoire', () => {
  test('l’onglet s’ouvre avant l’attente, puis reçoit le PDF, et l’URL est libérée plus tard', async () => {
    let tabOpenedBeforeFetch = false;
    const { tab, browser } = fakeBrowser();
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => {
        tabOpenedBeforeFetch = browser.openTab.mock.calls.length === 1;
        return pdf();
      }),
    );
    await openConvocation(12, browser);
    expect(tabOpenedBeforeFetch).toBe(true);
    expect(tab.show).toHaveBeenCalledWith('blob:convocation');
    expect(browser.revoke).not.toHaveBeenCalled();
    browser.later.mock.calls[0][0]();
    expect(browser.revoke).toHaveBeenCalledWith('blob:convocation');
  });
  test('en erreur, l’onglet est refermé : jamais d’onglet vide', async () => {
    const { tab, browser } = fakeBrowser();
    respondWith(problem(409));
    await expect(openConvocation(12, browser)).rejects.toThrow(MESSAGES[409]);
    expect(tab.close).toHaveBeenCalled();
    expect(tab.show).not.toHaveBeenCalled();
    expect(browser.objectUrl).not.toHaveBeenCalled();
  });
  test('onglet bloqué : une phrase, et aucune requête inutile', async () => {
    const { browser } = fakeBrowser(false);
    respondWith(pdf());
    await expect(openConvocation(12, browser)).rejects.toThrow(POPUP);
    expect(fetch).not.toHaveBeenCalled();
  });
  test('Enregistrer : download=true, nom du serveur, URL libérée', async () => {
    const { browser } = fakeBrowser();
    respondWith(pdf('attachment; filename="convocation-12.pdf"'));
    await saveConvocation(12, browser);
    expect(fetch).toHaveBeenCalledWith(
      expect.stringContaining('?download=true'),
      expect.anything(),
    );
    expect(browser.save).toHaveBeenCalledWith('blob:convocation', 'convocation-12.pdf');
    browser.later.mock.calls[0][0]();
    expect(browser.revoke).toHaveBeenCalledWith('blob:convocation');
  });
});
