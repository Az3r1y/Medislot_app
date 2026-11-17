import { ref } from 'vue';

// Routeur minimal de la console : l'URL reste la source de vérité (partage, rechargement).
export type Route =
  | { name: 'login' }
  | { name: 'clinics' }
  | { name: 'new' }
  | { name: 'clinic'; slug: string }
  | { name: 'edit'; slug: string }
  | { name: 'journal'; slug: string }
  | { name: 'appointments'; slug: string; practitioner: number }
  | { name: 'dashboard' }
  | { name: 'master-journal' }
  | { name: 'not-found' };

const SLUG = '([a-z0-9]+(?:-[a-z0-9]+)*)';

export function parse(path: string): Route {
  const p = path.replace(/\/+$/, '') || '/';
  if (p === '/platform' || p === '/platform/clinics') return { name: 'clinics' };
  if (p === '/platform/login') return { name: 'login' };
  if (p === '/platform/dashboard') return { name: 'dashboard' };
  if (p === '/platform/journal') return { name: 'master-journal' };
  if (p === '/platform/clinics/new') return { name: 'new' };
  let m = new RegExp(`^/platform/clinics/${SLUG}$`).exec(p);
  if (m) return { name: 'clinic', slug: m[1] };
  m = new RegExp(`^/platform/clinics/${SLUG}/edit$`).exec(p);
  if (m) return { name: 'edit', slug: m[1] };
  m = new RegExp(`^/platform/clinics/${SLUG}/journal$`).exec(p);
  if (m) return { name: 'journal', slug: m[1] };
  m = new RegExp(`^/platform/clinics/${SLUG}/practitioners/(\\d{1,18})$`).exec(p);
  if (m) return { name: 'appointments', slug: m[1], practitioner: Number(m[2]) };
  return { name: 'not-found' };
}

const location = () => globalThis.window?.location;
export const route = ref<Route>(parse(location()?.pathname ?? '/platform'));
export const search = ref(location()?.search ?? '');

export function navigate(path: string, replace = false) {
  const w = globalThis.window;
  if (!w) return;
  if (replace) w.history.replaceState(null, '', path);
  else w.history.pushState(null, '', path);
  sync();
  w.scrollTo?.({ top: 0 });
}

export function sync() {
  route.value = parse(location()?.pathname ?? '/platform');
  search.value = location()?.search ?? '';
}

globalThis.window?.addEventListener?.('popstate', sync);

// Liens internes : un vrai <a href> (clic molette, nouvel onglet) sans recharger la page.
export function follow(event: MouseEvent, path: string) {
  if (event.defaultPrevented || event.button !== 0) return;
  if (event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) return;
  event.preventDefault();
  navigate(path);
}
