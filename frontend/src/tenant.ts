export const SLUG = /^[a-z0-9]+(?:-[a-z0-9]+)*$/;
export const DEFAULT_COLOR = '#176b5b';
export const validSlug = (slug: string) => slug.length <= 63 && SLUG.test(slug);

export function clinicFromPath(path: string): string | null {
  const slug = /^\/c\/([^/]+)(?:\/|$)/.exec(path)?.[1];
  return slug && validSlug(slug) ? slug : null;
}
export function clinicFromHost(host: string): string | null {
  const hostname = host.toLowerCase().replace(/:\d+$/, '');
  const suffix = '.medislot.test';
  if (!hostname.endsWith(suffix)) return null;
  const slug = hostname.slice(0, -suffix.length);
  return validSlug(slug) ? slug : null;
}
export function safeColor(color: unknown): string {
  return typeof color === 'string' && /^#[0-9a-f]{6}$/i.test(color)
    ? color.toLowerCase()
    : DEFAULT_COLOR;
}
export function clinicPath(slug: string): string {
  if (!validSlug(slug)) throw new Error('Cabinet invalide.');
  return `/c/${slug}/`;
}
