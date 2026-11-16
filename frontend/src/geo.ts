// « Autour de moi ». La position n'est jamais stockée et ne sort du navigateur qu'arrondie.

export interface Position {
  lat: number;
  lon: number;
  label: string;
}

export class GeoError extends Error {}

// Un quartier suffit : inutile de réveiller le GPS.
export const POSITION_OPTIONS: PositionOptions = {
  enableHighAccuracy: false,
  timeout: 10_000,
  maximumAge: 60_000,
};

export const POSITION_MESSAGES = {
  insecure: 'La localisation exige une connexion sécurisée (HTTPS). Saisissez plutôt une adresse.',
  unsupported: 'Ce navigateur ne sait pas donner votre position. Saisissez plutôt une adresse.',
  1: 'Vous avez refusé la localisation. Saisissez une adresse ou un code postal, ou autorisez la position dans les réglages du site.',
  2: 'Votre position est introuvable pour le moment. Saisissez une adresse ou un code postal.',
  3: 'La localisation a pris trop de temps. Réessayez, ou saisissez une adresse.',
} as const;

/** À appeler depuis un clic, jamais au chargement de la page. */
export function getPosition(
  geo: Geolocation | undefined = globalThis.navigator?.geolocation,
  secure: boolean = globalThis.isSecureContext ?? false,
): Promise<Position> {
  // Hors HTTPS, le navigateur refuse sans même demander.
  if (!secure) return Promise.reject(new GeoError(POSITION_MESSAGES.insecure));
  if (!geo) return Promise.reject(new GeoError(POSITION_MESSAGES.unsupported));
  return new Promise((resolve, reject) =>
    geo.getCurrentPosition(
      (p) =>
        resolve({
          lat: roundCoord(p.coords.latitude),
          lon: roundCoord(p.coords.longitude),
          label: 'votre position',
        }),
      (e) => reject(new GeoError(POSITION_MESSAGES[e.code as 1 | 2 | 3] ?? POSITION_MESSAGES[2])),
      POSITION_OPTIONS,
    ),
  );
}

/** 3 décimales, soit environ 110 m. */
export const roundCoord = (x: number) => Math.round(x * 1000) / 1000;

export const RADIUS_KM = 5;

export function nearbyParams(
  p: { lat: number; lon: number },
  specialty = '',
  page = 0,
  size = 6,
): URLSearchParams {
  return new URLSearchParams({
    lat: String(roundCoord(p.lat)),
    lon: String(roundCoord(p.lon)),
    radiusKm: String(RADIUS_KM),
    specialty,
    page: String(page),
    size: String(size),
  });
}

/** 0,2 -> « 200 m », 1,5 -> « 1,5 km ». */
export function formatDistance(km: number): string {
  if (km < 1) return `${Math.round(km * 1000)} m`;
  return `${km.toLocaleString('fr-FR', { maximumFractionDigits: 1 })} km`;
}

// Géocodage : Géoplateforme IGN, service public sans clé.
export const GEOCODE_URL = 'https://data.geopf.fr/geocodage/search';
export const GEOCODE_MESSAGES = {
  empty: 'Saisissez une adresse ou un code postal.',
  notFound: 'Adresse introuvable. Vérifiez la saisie ou essayez un code postal.',
  busy: 'Le service d’adresses est saturé. Réessayez dans quelques secondes.',
  failed: 'Le service d’adresses ne répond pas. Réessayez plus tard.',
  offline: 'Connexion impossible. Vérifiez le réseau puis réessayez.',
};
// En dessous, le service devine plus qu'il ne trouve.
export const MIN_SCORE = 0.4;

const cache = new Map<string, Promise<Position>>();
export const clearGeocodeCache = () => cache.clear();
const normalize = (q: string) => q.trim().replace(/\s+/g, ' ').toLowerCase();

/** Un seul appel par adresse ; un échec n'est pas gardé en cache. */
export function geocode(input: string): Promise<Position> {
  const key = normalize(input);
  if (!key) return Promise.reject(new GeoError(GEOCODE_MESSAGES.empty));
  const known = cache.get(key);
  if (known) return known;
  const pending = (async () => {
    let r: Response;
    try {
      r = await fetch(`${GEOCODE_URL}?${new URLSearchParams({ q: key, limit: '1' })}`);
    } catch {
      throw new GeoError(GEOCODE_MESSAGES.offline);
    }
    if (r.status === 429) throw new GeoError(GEOCODE_MESSAGES.busy);
    if (!r.ok) throw new GeoError(GEOCODE_MESSAGES.failed);
    const f = (await r.json())?.features?.[0];
    if (!f || (f.properties?.score ?? 0) < MIN_SCORE) throw new GeoError(GEOCODE_MESSAGES.notFound);
    // GeoJSON : longitude d'abord.
    const [lon, lat] = f.geometry.coordinates as [number, number];
    return { lat: roundCoord(lat), lon: roundCoord(lon), label: String(f.properties.label) };
  })();
  cache.set(key, pending);
  pending.catch(() => cache.delete(key));
  return pending;
}
