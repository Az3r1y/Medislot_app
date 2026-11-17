// Formats et règles partagés par les écrans de la console master.
export const ZONE = 'Europe/Paris';
export const SLUG = /^[a-z0-9]+(?:-[a-z0-9]+)*$/;
export const COLOR = /^#[0-9a-f]{6}$/i;
export const LOGO_MAX = 200 * 1024;

// Même règle que le back, qui reste l'arbitre (unicité en base).
export function slugError(slug: string): string {
  if (!slug) return 'Saisissez l’identifiant du cabinet.';
  if (slug.length < 3 || slug.length > 40) return '3 à 40 caractères.';
  if (!SLUG.test(slug)) return 'Minuscules, chiffres et tirets uniquement.';
  return '';
}

// Proposition d'identifiant à partir du nom : « Cabinet de la Part-Dieu » → « part-dieu ».
export function suggestSlug(name: string): string {
  const words = name
    .normalize('NFD')
    .replace(/[̀-ͯ]/g, '')
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, ' ')
    .trim()
    .split(' ')
    .filter(Boolean);
  const stop = new Set(['cabinet', 'centre', 'maison', 'de', 'du', 'des', 'la', 'le', 'les', 'l']);
  const kept = words.filter((w) => !stop.has(w));
  return (kept.length ? kept : words).join('-').slice(0, 40).replace(/-+$/, '');
}

export function logoError(file: { size: number; type: string } | null): string {
  if (!file) return '';
  if (!['image/png', 'image/svg+xml'].includes(file.type)) return 'Logo au format PNG ou SVG.';
  if (file.size > LOGO_MAX) return 'Logo de 200 Ko maximum.';
  return '';
}

const fmt = (options: Intl.DateTimeFormatOptions) =>
  new Intl.DateTimeFormat('fr-FR', { timeZone: ZONE, ...options });
export const dateLabel = (iso: string) =>
  fmt({ day: '2-digit', month: '2-digit', year: 'numeric' }).format(new Date(iso));
export const shortDate = (iso: string) =>
  fmt({ day: '2-digit', month: '2-digit' }).format(new Date(iso));
export const timeLabel = (iso: string) =>
  fmt({ hour: '2-digit', minute: '2-digit' }).format(new Date(iso));
export const dayLabel = (iso: string) =>
  `${fmt({ weekday: 'short' }).format(new Date(iso))} ${shortDate(iso)}`;
export const slotLabel = (iso: string) => `${dayLabel(iso)} · ${timeLabel(iso)}`;

// Dates sans heure (AAAA-MM-JJ), calculées en heure de Paris.
export function today(now = new Date()): string {
  return new Intl.DateTimeFormat('en-CA', { timeZone: ZONE }).format(now);
}
export function addDays(day: string, days: number): string {
  const d = new Date(`${day}T12:00:00Z`);
  d.setUTCDate(d.getUTCDate() + days);
  return d.toISOString().slice(0, 10);
}
export function mondayOf(day: string): string {
  const weekday = new Date(`${day}T12:00:00Z`).getUTCDay();
  return addDays(day, weekday === 0 ? -6 : 1 - weekday);
}
export const plainDate = (day: string) => `${day.slice(8, 10)}/${day.slice(5, 7)}`;

export const PERIODS = [
  { days: 7, label: '7 derniers jours' },
  { days: 30, label: '30 derniers jours' },
  { days: 90, label: '90 derniers jours' },
];
export function period(days: number, now = new Date()) {
  const to = today(now);
  return { from: addDays(to, -(days - 1)), to };
}

export const percent = (part: number, total: number) =>
  total > 0 ? Math.round((part / total) * 100) : 0;

export const actionLabels: Record<string, string> = {
  CREATED: 'Rendez-vous réservé',
  CANCELLED: 'Rendez-vous annulé',
  RESCHEDULED: 'Rendez-vous déplacé',
  LOGIN: 'Connexion à la console',
  CLINIC_CREATED: 'Cabinet créé',
  CLINIC_UPDATED: 'Cabinet modifié',
  CLINIC_DELETED: 'Cabinet supprimé',
  LOGO_UPDATED: 'Logo modifié',
  AUDIT_VIEWED: 'Journal consulté',
  AUDIT_EXPORTED: 'Journal exporté',
  APPOINTMENTS_VIEWED: 'Rendez-vous consultés',
};
export const roleLabels: Record<string, string> = {
  PATIENT: 'Patient',
  SECRETARY: 'Secrétaire',
  ADMIN: 'Admin cabinet',
  PLATFORM_ADMIN: 'Master',
};
export const statusLabels: Record<string, string> = {
  CONFIRMED: 'Confirmé',
  PAST: 'Passé',
  CANCELLED: 'Annulé',
};
export const label = (labels: Record<string, string>, key: string) => labels[key] ?? key;

export interface AuditFilters {
  action: string;
  role: string;
  from: string;
  to: string;
}
// Mêmes paramètres pour la liste et l'export CSV : l'export reprend les filtres affichés.
export function auditQuery(filters: AuditFilters, cursor?: string | null): string {
  const params = new URLSearchParams();
  for (const [key, value] of Object.entries(filters)) if (value) params.set(key, value);
  if (cursor) params.set('cursor', cursor);
  const text = params.toString();
  return text ? `?${text}` : '';
}

// Texte lisible sur la couleur d'un cabinet (contraste WCAG le plus élevé entre blanc et bleu nuit).
export function inkOn(color: string): string {
  if (!COLOR.test(color)) return '#ffffff';
  const [r, g, b] = [1, 3, 5].map((i) => {
    const c = parseInt(color.slice(i, i + 2), 16) / 255;
    return c <= 0.03928 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4;
  });
  const l = 0.2126 * r + 0.7152 * g + 0.0722 * b;
  const navy = 0.0302; // luminance de #12334b
  return 1.05 / (l + 0.05) >= (l + 0.05) / (navy + 0.05) ? '#ffffff' : '#12334b';
}
