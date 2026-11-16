import { beforeEach, afterEach, describe, it, expect, vi } from 'vitest';
import { createPinia, setActivePinia } from 'pinia';
import { useClinic, type Clinic } from './stores/clinic';
import { useAgenda } from './stores/agenda';
import { api } from './api';
import { fetchConvocation } from './convocation';

const clinics: Clinic[] = [
  { id: 1, slug: 'cabinet-a', name: 'Paris', city: 'Paris', color: '#176b5b' },
  { id: 2, slug: 'cabinet-b', name: 'Lyon', city: 'Lyon', color: '#3955a5' },
];
const json = (data: unknown) => new Response(JSON.stringify(data), { status: 200 });
beforeEach(() => {
  setActivePinia(createPinia());
  vi.stubGlobal('window', {
    location: { pathname: '/c/cabinet-a/', host: 'localhost' },
    history: { pushState: vi.fn(), replaceState: vi.fn() },
  });
  useClinic().clinics = structuredClone(clinics);
});
afterEach(() => {
  vi.unstubAllGlobals();
  setActivePinia(undefined);
});

describe('Store et transport du cabinet', () => {
  it('envoie X-Clinic et empêche un appelant de le remplacer', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => json([])),
    );
    await api('/professionals', { headers: { 'X-Clinic': 'pirate' } });
    const options = vi.mocked(fetch).mock.calls[0][1]!;
    expect(new Headers(options.headers).get('X-Clinic')).toBe('cabinet-a');
  });
  it('switchTo change l’URL et vide immédiatement le cache', () => {
    const agenda = useAgenda();
    agenda.appointments = [{ id: 42 }] as typeof agenda.appointments;
    expect(useClinic().switchTo('cabinet-b')).toBe(true);
    expect(useClinic().slug).toBe('cabinet-b');
    expect(agenda.appointments).toEqual([]);
    expect(window.history.pushState).toHaveBeenCalledWith(null, '', '/c/cabinet-b/');
  });
  it('refuse de basculer vers un cabinet non autorisé', () => {
    const clinic = useClinic();
    clinic.authenticated = true;
    clinic.mine = [clinics[0]];
    expect(() => clinic.switchTo('cabinet-b')).toThrow('accès');
    expect(clinic.slug).toBe('cabinet-a');
    expect(window.history.pushState).not.toHaveBeenCalled();
  });
  it('jette une réponse lente arrivée après un changement de cabinet', async () => {
    let resolve!: (response: Response) => void;
    vi.stubGlobal(
      'fetch',
      vi.fn(() => new Promise<Response>((r) => (resolve = r))),
    );
    const agenda = useAgenda();
    const loading = agenda.load();
    useClinic().switchTo('cabinet-b');
    resolve(json([{ id: 42 }]));
    await loading;
    expect(agenda.appointments).toEqual([]);
    expect(agenda.loading).toBe(false);
  });
  it('afterLogin garde le cabinet courant s’il fait partie des appartenances', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => json(clinics)),
    );
    expect(await useClinic().afterLogin()).toBe(false);
    expect(useClinic().mine).toEqual(clinics);
    expect(useClinic().slug).toBe('cabinet-a');
  });
  it('afterLogin bascule sur le premier cabinet accessible sinon', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => json([clinics[1]])),
    );
    expect(await useClinic().afterLogin()).toBe(true);
    expect(useClinic().slug).toBe('cabinet-b');
  });
  it('protège également le téléchargement des PDF par X-Clinic', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => new Response('%PDF', { headers: { 'Content-Type': 'application/pdf' } })),
    );
    await fetchConvocation(7);
    expect(new Headers(vi.mocked(fetch).mock.calls[0][1]?.headers).get('X-Clinic')).toBe(
      'cabinet-a',
    );
  });
  it('un aller-retour A-B-A ne rend pas une ancienne réponse valide', async () => {
    let resolve!: (response: Response) => void;
    vi.stubGlobal(
      'fetch',
      vi.fn(() => new Promise<Response>((r) => (resolve = r))),
    );
    const loading = useAgenda().load();
    useClinic().switchTo('cabinet-b');
    useClinic().switchTo('cabinet-a');
    resolve(json([{ id: 99 }]));
    await loading;
    expect(useAgenda().appointments).toEqual([]);
  });
});
