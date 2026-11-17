import { afterEach, describe, expect, it, vi } from 'vitest';
import {
  addDays,
  auditQuery,
  inkOn,
  logoError,
  mondayOf,
  percent,
  period,
  slugError,
  suggestSlug,
  today,
} from './format';
import { parse } from './router';
import { platformApi } from './platformApi';
import { problemOf } from './context';
import { ApiError } from '../api';

afterEach(() => vi.unstubAllGlobals());

describe('Console master : règles du formulaire', () => {
  it('contrôle l’identifiant comme le back', () => {
    expect(slugError('part-dieu')).toBe('');
    expect(slugError('ab')).toBe('3 à 40 caractères.');
    expect(slugError('Part_Dieu')).toBe('Minuscules, chiffres et tirets uniquement.');
    expect(slugError('-part')).not.toBe('');
    expect(slugError('a'.repeat(41))).toBe('3 à 40 caractères.');
  });
  it('propose un identifiant à partir du nom', () => {
    expect(suggestSlug('Cabinet de la Part-Dieu')).toBe('part-dieu');
    expect(suggestSlug('Maison de santé Croix-Rousse')).toBe('sante-croix-rousse');
    expect(slugError(suggestSlug('Centre médical Gerland'))).toBe('');
  });
  it('refuse un logo trop lourd ou d’un autre format', () => {
    expect(logoError({ size: 1000, type: 'image/png' })).toBe('');
    expect(logoError({ size: 300 * 1024, type: 'image/svg+xml' })).toBe('Logo de 200 Ko maximum.');
    expect(logoError({ size: 1000, type: 'image/jpeg' })).toBe('Logo au format PNG ou SVG.');
  });
  it('choisit un texte lisible sur la couleur du cabinet', () => {
    expect(inkOn('#0873b9')).toBe('#ffffff');
    expect(inkOn('#ffd966')).toBe('#12334b');
  });
});

describe('Console master : dates en heure de Paris', () => {
  it('calcule le jour à Paris, pas en UTC', () => {
    expect(today(new Date('2026-11-16T23:30:00Z'))).toBe('2026-11-17');
  });
  it('donne le lundi de la semaine et les périodes glissantes', () => {
    expect(mondayOf('2026-11-22')).toBe('2026-11-16');
    expect(mondayOf('2026-11-16')).toBe('2026-11-16');
    expect(addDays('2026-03-28', 2)).toBe('2026-03-30');
    expect(period(30, new Date('2026-11-17T10:00:00Z'))).toEqual({
      from: '2026-10-19',
      to: '2026-11-17',
    });
    expect(percent(95, 885)).toBe(11);
    expect(percent(1, 0)).toBe(0);
  });
});

describe('Console master : routes et export', () => {
  it('lit les adresses de la console', () => {
    expect(parse('/platform')).toEqual({ name: 'clinics' });
    expect(parse('/platform/clinics/brotteaux/practitioners/12')).toEqual({
      name: 'appointments',
      slug: 'brotteaux',
      practitioner: 12,
    });
    expect(parse('/platform/clinics/brotteaux/journal/')).toEqual({
      name: 'journal',
      slug: 'brotteaux',
    });
    expect(parse('/platform/clinics/../x')).toEqual({ name: 'not-found' });
  });
  it('l’export reprend exactement les filtres affichés', () => {
    const filters = { action: 'CANCELLED', role: '', from: '2026-11-01', to: '2026-11-17' };
    expect(auditQuery(filters)).toBe('?action=CANCELLED&from=2026-11-01&to=2026-11-17');
    expect(auditQuery(filters, 'abc')).toBe(
      '?action=CANCELLED&from=2026-11-01&to=2026-11-17&cursor=abc',
    );
  });
});

describe('Console master : transport', () => {
  it('n’envoie jamais X-Clinic et joint le jeton CSRF aux écritures', async () => {
    const fetchMock = vi.fn(async (url: string) =>
      url === '/api/csrf'
        ? new Response(JSON.stringify({ token: 't', headerName: 'X-XSRF-TOKEN' }))
        : new Response(null, { status: 204 }),
    );
    vi.stubGlobal('fetch', fetchMock);
    await platformApi('/clinics/a', { method: 'DELETE', body: '{}' });
    const [url, init] = fetchMock.mock.calls.at(-1) as unknown as [string, RequestInit];
    const headers = new Headers(init.headers);
    expect(url).toBe('/api/platform/clinics/a');
    expect(headers.get('X-XSRF-TOKEN')).toBe('t');
    expect(headers.has('X-Clinic')).toBe(false);
  });
  it('rattache les erreurs 409 au champ concerné', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(
        async () =>
          new Response(
            JSON.stringify({
              detail: 'Cet identifiant est déjà utilisé.',
              errors: [{ field: 'slug', message: 'Cet identifiant est déjà utilisé.' }],
            }),
            { status: 409 },
          ),
      ),
    );
    const error = (await platformApi('/clinics').catch((e: unknown) => e)) as ApiError;
    expect(error).toBeInstanceOf(ApiError);
    expect(error.fields.slug).toBe('Cet identifiant est déjà utilisé.');
  });
  it('affiche le même composant d’erreur pour 401, 403 et 404', () => {
    expect(problemOf(new ApiError('x', 401))?.message).toBe(
      'Votre session a expiré. Reconnectez-vous.',
    );
    expect(problemOf(new ApiError('x', 403))?.message).toBe(
      'Cette zone est réservée à l’équipe plateforme.',
    );
    expect(problemOf(new ApiError('Ce cabinet n’existe pas ou a été supprimé.', 404))?.status).toBe(
      404,
    );
    expect(problemOf(new ApiError('x', 500))).toBeNull();
  });
});
