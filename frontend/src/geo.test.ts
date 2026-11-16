import { afterEach, beforeEach, describe, expect, test, vi } from 'vitest';
import {
  GEOCODE_MESSAGES,
  POSITION_MESSAGES,
  POSITION_OPTIONS,
  clearGeocodeCache,
  formatDistance,
  geocode,
  getPosition,
  nearbyParams,
  roundCoord,
} from './geo';

function fakeGeolocation(answer: { lat: number; lon: number } | { code: number }) {
  return {
    getCurrentPosition: vi.fn(
      (ok: PositionCallback, ko?: PositionErrorCallback | null, _o?: PositionOptions) => {
        if ('code' in answer) ko?.({ code: answer.code } as GeolocationPositionError);
        else ok({ coords: { latitude: answer.lat, longitude: answer.lon } } as GeolocationPosition);
      },
    ),
  } as unknown as Geolocation & { getCurrentPosition: ReturnType<typeof vi.fn> };
}

describe('Position', () => {
  test('Bellecour : position arrondie, options transmises', async () => {
    const geo = fakeGeolocation({ lat: 45.757612, lon: 4.831734 });
    await expect(getPosition(geo, true)).resolves.toEqual({
      lat: 45.758,
      lon: 4.832,
      label: 'votre position',
    });
    expect(geo.getCurrentPosition.mock.calls[0][2]).toBe(POSITION_OPTIONS);
  });
  test.each([1, 2, 3] as const)('erreur %i : une phrase dédiée', async (code) => {
    await expect(getPosition(fakeGeolocation({ code }), true)).rejects.toThrow(
      POSITION_MESSAGES[code],
    );
  });
  test('hors contexte sécurisé : refus sans rien demander au navigateur', async () => {
    const geo = fakeGeolocation({ lat: 45.75, lon: 4.83 });
    await expect(getPosition(geo, false)).rejects.toThrow(POSITION_MESSAGES.insecure);
    expect(geo.getCurrentPosition).not.toHaveBeenCalled();
  });
  test('navigateur sans géolocalisation', async () => {
    await expect(getPosition(undefined, true)).rejects.toThrow(POSITION_MESSAGES.unsupported);
  });
});

describe('Paramètres', () => {
  test('arrondi à 3 décimales (~110 m)', () => {
    expect(roundCoord(45.757612)).toBe(45.758);
    expect(roundCoord(-0.57749)).toBe(-0.577);
  });
  test('noms exacts du contrat, lat avant lon, rayon 5 km', () => {
    expect(nearbyParams({ lat: 45.7576, lon: 4.8317 }, 'cardiology').toString()).toBe(
      'lat=45.758&lon=4.832&radiusKm=5&specialty=cardiology&page=0&size=6',
    );
  });
});

describe('Affichage', () => {
  test('mètres sous 1 km', () => {
    expect(formatDistance(0.2)).toBe('200 m');
    expect(formatDistance(0.04)).toBe('40 m');
  });
  test('kilomètres à la française au-delà', () => {
    expect(formatDistance(1)).toBe('1 km');
    expect(formatDistance(3.9)).toBe('3,9 km');
    expect(formatDistance(392.3)).toBe('392,3 km');
  });
});

describe('Géocodage', () => {
  const jsonResponse = (body: unknown, status = 200) =>
    new Response(JSON.stringify(body), { status });
  // Réponse réelle de data.geopf.fr pour q=69002, abrégée.
  const LYON_2 = {
    features: [
      {
        geometry: { type: 'Point', coordinates: [4.826148, 45.750351] },
        properties: { label: 'Lyon 2e Arrondissement', score: 0.86, type: 'municipality' },
      },
    ],
  };
  beforeEach(() => clearGeocodeCache());
  afterEach(() => vi.unstubAllGlobals());

  test('lit coordinates dans l’ordre GeoJSON : [longitude, latitude]', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => jsonResponse(LYON_2)),
    );
    await expect(geocode('69002')).resolves.toEqual({
      lat: 45.75,
      lon: 4.826,
      label: 'Lyon 2e Arrondissement',
    });
    expect(String(vi.mocked(fetch).mock.calls[0][0])).toBe(
      'https://data.geopf.fr/geocodage/search?q=69002&limit=1',
    );
  });
  test('cache : espaces et majuscules ne refont pas d’appel', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => jsonResponse(LYON_2)),
    );
    await geocode('Place Bellecour  Lyon');
    await geocode('  place bellecour lyon ');
    expect(fetch).toHaveBeenCalledTimes(1);
  });
  test('limite du service (429) : une phrase, et l’échec n’est pas mis en cache', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => jsonResponse({}, 429)),
    );
    await expect(geocode('69002')).rejects.toThrow(GEOCODE_MESSAGES.busy);
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => jsonResponse(LYON_2)),
    );
    await expect(geocode('69002')).resolves.toMatchObject({ label: 'Lyon 2e Arrondissement' });
  });
  test('aucun résultat, ou résultat trop incertain', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => jsonResponse({ features: [] })),
    );
    await expect(geocode('zzzz')).rejects.toThrow(GEOCODE_MESSAGES.notFound);
    vi.stubGlobal(
      'fetch',
      vi.fn(async () =>
        jsonResponse({
          features: [{ ...LYON_2.features[0], properties: { label: 'x', score: 0.2 } }],
        }),
      ),
    );
    await expect(geocode('rue imaginaire')).rejects.toThrow(GEOCODE_MESSAGES.notFound);
  });
  test('saisie vide : aucun appel ; réseau coupé : message hors ligne', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => {
        throw new TypeError('Failed to fetch');
      }),
    );
    await expect(geocode('   ')).rejects.toThrow(GEOCODE_MESSAGES.empty);
    expect(fetch).not.toHaveBeenCalled();
    await expect(geocode('69002')).rejects.toThrow(GEOCODE_MESSAGES.offline);
  });
});
