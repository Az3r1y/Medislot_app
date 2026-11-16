import { beforeEach, afterEach, it, expect, vi } from 'vitest';
import { createPinia, setActivePinia } from 'pinia';
import { useClinic } from './stores/clinic';
import { fetchCalendar } from './calendar';
beforeEach(() => {
  setActivePinia(createPinia());
  vi.stubGlobal('window', {
    location: { pathname: '/c/cabinet-a/', host: 'localhost' },
    history: { pushState: vi.fn() },
  });
  useClinic().clinics = [
    { id: 1, slug: 'cabinet-a', name: 'A', city: 'Paris', color: '#176b5b' },
    { id: 2, slug: 'cabinet-b', name: 'B', city: 'Lyon', color: '#3955a5' },
  ];
});
afterEach(() => {
  vi.unstubAllGlobals();
  setActivePinia(undefined);
});
it('envoie le cabinet lors du téléchargement ICS', async () => {
  vi.stubGlobal(
    'fetch',
    vi.fn(
      async () => new Response('BEGIN:VCALENDAR', { headers: { 'Content-Type': 'text/calendar' } }),
    ),
  );
  expect(await (await fetchCalendar(1)).text()).toContain('VCALENDAR');
  expect(new Headers(vi.mocked(fetch).mock.calls[0][1]?.headers).get('X-Clinic')).toBe('cabinet-a');
});
it('rejette un téléchargement arrivé après le changement de cabinet', async () => {
  let resolve!: (r: Response) => void;
  vi.stubGlobal(
    'fetch',
    vi.fn(() => new Promise<Response>((r) => (resolve = r))),
  );
  const pending = fetchCalendar(1);
  useClinic().switchTo('cabinet-b');
  resolve(new Response('BEGIN:VCALENDAR', { headers: { 'Content-Type': 'text/calendar' } }));
  await expect(pending).rejects.toThrow('cabinet a changé');
});
it('refuse une page HTML ou une erreur à la place du calendrier', async () => {
  vi.stubGlobal(
    'fetch',
    vi.fn(async () => new Response('<html>', { headers: { 'Content-Type': 'text/html' } })),
  );
  await expect(fetchCalendar(1)).rejects.toThrow('indisponible');
});
