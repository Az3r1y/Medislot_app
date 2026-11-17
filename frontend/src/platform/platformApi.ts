import { ApiError } from '../api';

// Client de la console master : jamais d'en-tête X-Clinic, les routes /api/platform/** n'ont pas
// de cabinet courant.
let csrf: { token: string; headerName: string } | null = null;
const SAFE = ['GET', 'HEAD', 'OPTIONS'];

export async function refreshPlatformCsrf() {
  const r = await fetch('/api/csrf', { credentials: 'same-origin' });
  if (!r.ok) throw new ApiError('Impossible de sécuriser la session.', r.status);
  csrf = await r.json();
}

export async function platformApi<T>(
  path: string,
  init: RequestInit = {},
  retried = false,
): Promise<T> {
  const method = (init.method || 'GET').toUpperCase();
  const headers = new Headers(init.headers);
  if (!SAFE.includes(method)) {
    if (!csrf) await refreshPlatformCsrf();
    headers.set(csrf!.headerName, csrf!.token);
  }
  if (typeof init.body === 'string') headers.set('Content-Type', 'application/json');
  const response = await fetch('/api/platform' + path, {
    ...init,
    headers,
    credentials: 'same-origin',
    signal: init.signal ?? AbortSignal.timeout(20_000),
  });
  // Écriture refusée : le jeton CSRF a pu changer avec la session, une seule nouvelle tentative.
  if (response.status === 403 && !retried && !SAFE.includes(method)) {
    csrf = null;
    return platformApi<T>(path, init, true);
  }
  if (!response.ok) {
    let message = 'Le service est momentanément indisponible.';
    const fields: Record<string, string> = {};
    try {
      const p = await response.json();
      message = p.detail || p.title || message;
      for (const e of p.errors ?? [])
        fields[e.field] = fields[e.field] ? `${fields[e.field]} ${e.message}` : e.message;
    } catch {}
    throw new ApiError(message, response.status, fields);
  }
  if (response.status === 204) return undefined as T;
  return response.json();
}

export const body = (data: unknown) => JSON.stringify(data);

// Déconnexion : route commune /api/logout, jeton CSRF obligatoire.
export async function platformLogout() {
  if (!csrf) await refreshPlatformCsrf();
  await fetch('/api/logout', {
    method: 'POST',
    credentials: 'same-origin',
    headers: { [csrf!.headerName]: csrf!.token },
  });
  csrf = null;
}
