import { clinicHeaders, clinicSnapshot, assertSameClinic } from './clinicRequest';
let csrf: { token: string; headerName: string } | null = null;
// fields : message par champ, tiré de errors[] d'un 400 de validation
export class ApiError extends Error {
  constructor(
    message: string,
    public status: number,
    public fields: Record<string, string> = {},
  ) {
    super(message);
  }
}
export async function refreshCsrf() {
  const r = await fetch('/api/csrf', { credentials: 'same-origin', headers: clinicHeaders() });
  if (!r.ok) throw new ApiError('Impossible de sécuriser la session.', r.status);
  csrf = await r.json();
}
export async function api<T>(path: string, init: RequestInit = {}, retried = false): Promise<T> {
  const snapshot = clinicSnapshot();
  const method = (init.method || 'GET').toUpperCase();
  const headers = new Headers(init.headers);
  for (const [key, value] of Object.entries(clinicHeaders())) headers.set(key, value);
  if (!['GET', 'HEAD', 'OPTIONS'].includes(method)) {
    if (!csrf) await refreshCsrf();
    headers.set(csrf!.headerName, csrf!.token);
  }
  if (init.body && !(init.body instanceof URLSearchParams))
    headers.set('Content-Type', 'application/json');
  assertSameClinic(snapshot);
  const response = await fetch('/api' + path, {
    ...init,
    headers,
    credentials: 'same-origin',
    signal: init.signal ?? AbortSignal.timeout(20_000),
  });
  assertSameClinic(snapshot);
  // 403 sur une écriture : jeton CSRF périmé (session renouvelée), on le renouvelle et on rejoue une seule fois
  if (response.status === 403 && !retried && !['GET', 'HEAD', 'OPTIONS'].includes(method)) {
    csrf = null;
    return api<T>(path, init, true);
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
    assertSameClinic(snapshot);
    const ref = response.headers.get('X-Trace-Id');
    if (response.status >= 500 && ref) message += ` Référence : ${ref}`;
    throw new ApiError(message, response.status, fields);
  }
  if (response.status === 204) return undefined as T;
  const result = await response.json();
  assertSameClinic(snapshot);
  return result;
}
