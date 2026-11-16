import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { createPinia, setActivePinia } from 'pinia';
import { useClinic } from './stores/clinic';
import { useSession } from './stores/session';
import { usePendingBookings } from './composables/usePendingBookings';

const booking = {
  slotId: 7,
  professionalName: 'Praticien A',
  startsAt: new Date(Date.now() + 86_400_000).toISOString(),
  measure: false,
};
beforeEach(() => {
  setActivePinia(createPinia());
  vi.stubGlobal('window', {
    location: { pathname: '/c/cabinet-a/', host: 'localhost' },
    history: { pushState: vi.fn() },
  });
  const saved = new Map<string, string>();
  vi.stubGlobal('localStorage', {
    getItem: (k: string) => saved.get(k) ?? null,
    setItem: (k: string, v: string) => saved.set(k, v),
  });
  useClinic().clinics = [
    { id: 1, slug: 'cabinet-a', name: 'A', city: 'Paris', color: '#176b5b' },
    { id: 2, slug: 'cabinet-b', name: 'B', city: 'Lyon', color: '#3955a5' },
  ];
  useSession().user = { id: 1, name: 'Test', email: 'test@example.test', role: 'PATIENT' };
});
afterEach(() => {
  vi.unstubAllGlobals();
  setActivePinia(undefined);
});

it('les réservations en attente sont séparées par cabinet et utilisateur', () => {
  const queue = usePendingBookings(vi.fn(), vi.fn());
  queue.add(booking);
  expect(queue.pending.value).toHaveLength(1);
  useClinic().switchTo('cabinet-b');
  expect(queue.pending.value).toHaveLength(0);
  useClinic().switchTo('cabinet-a');
  useSession().user = { ...useSession().user!, id: 2 };
  expect(queue.pending.value).toHaveLength(0);
  useSession().user = { ...useSession().user!, id: 1 };
  expect(queue.pending.value).toHaveLength(1);
});

it('une réservation de A ne se rejoue pas dans B', async () => {
  const queue = usePendingBookings(vi.fn(), vi.fn());
  queue.add(booking);
  useClinic().switchTo('cabinet-b');
  vi.stubGlobal('fetch', vi.fn());
  expect(await queue.flush()).toBe(false);
  expect(fetch).not.toHaveBeenCalled();
});

it('un changement pendant le renouvellement CSRF empêche tout envoi', async () => {
  let resolve!: (r: Response) => void;
  vi.stubGlobal(
    'fetch',
    vi.fn(() => new Promise<Response>((r) => (resolve = r))),
  );
  const queue = usePendingBookings(vi.fn(), vi.fn());
  queue.add(booking);
  const flush = queue.flush();
  useClinic().switchTo('cabinet-b');
  resolve(new Response(JSON.stringify({ token: 'token', headerName: 'X-CSRF-TOKEN' })));
  expect(await flush).toBe(false);
  expect(fetch).toHaveBeenCalledTimes(1);
  useClinic().switchTo('cabinet-a');
  expect(queue.pending.value).toHaveLength(1);
});
