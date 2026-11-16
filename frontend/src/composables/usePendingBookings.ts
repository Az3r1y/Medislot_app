import { ref, computed } from 'vue';
import { useClinic } from '../stores/clinic';
import { useSession } from '../stores/session';
import { api, ApiError, refreshCsrf } from '../api';
import { dateLabel, timeLabel, type Appointment } from '../types';
export interface PendingBooking {
  clinicSlug: string;
  userId: number;
  slotId: number;
  professionalName: string;
  startsAt: string;
  measure: boolean;
  queuedAt: string;
  attempts: number;
}
const KEY = 'medislot-pending-bookings',
  MAX_AGE = 30 * 60_000,
  MAX_ATTEMPTS = 3;
const read = (): PendingBooking[] => {
  try {
    const items: PendingBooking[] = JSON.parse(localStorage.getItem(KEY) || '[]');
    return items.filter(
      (item) => typeof item.clinicSlug === 'string' && typeof item.userId === 'number',
    );
  } catch {
    return [];
  }
};
// réservations faites pendant une coupure : gardées sur l'appareil puis rejouées au retour du service
export function usePendingBookings(
  notify: (text: string, ok: boolean) => void,
  onExpired: () => void,
) {
  const all = ref<PendingBooking[]>(read());
  const clinic = useClinic();
  const session = useSession();
  const pending = computed(() =>
    all.value.filter((b) => b.clinicSlug === clinic.slug && b.userId === session.user?.id),
  );
  let flushing = false;
  const save = () => {
    try {
      localStorage.setItem(KEY, JSON.stringify(all.value));
    } catch {}
  };
  const drop = (booking: PendingBooking) => {
    all.value = all.value.filter(
      (p) =>
        p.slotId !== booking.slotId ||
        p.userId !== booking.userId ||
        p.clinicSlug !== booking.clinicSlug,
    );
    save();
  };
  const label = (b: PendingBooking) =>
    `${b.professionalName}, ${dateLabel(b.startsAt)} à ${timeLabel(b.startsAt)}`;
  function add(b: Omit<PendingBooking, 'queuedAt' | 'attempts' | 'clinicSlug' | 'userId'>) {
    if (!session.user) return;
    if (!pending.value.some((p) => p.slotId === b.slotId))
      all.value.push({
        ...b,
        clinicSlug: clinic.slug,
        userId: session.user.id,
        queuedAt: new Date().toISOString(),
        attempts: 0,
      });
    save();
  }
  /** Rejoue la file ; renvoie true si au moins une réservation a été tranchée (confirmée ou abandonnée). */
  async function flush() {
    if (flushing || !pending.value.length) return false;
    const currentClinic = clinic.slug,
      currentUser = session.user?.id,
      revision = clinic.revision;
    flushing = true;
    let changed = false;
    try {
      await refreshCsrf(); // le jeton a pu expirer pendant la coupure
      for (const b of [...pending.value]) {
        if (
          currentClinic !== clinic.slug ||
          currentUser !== session.user?.id ||
          revision !== clinic.revision
        )
          break;
        if (Date.now() - Date.parse(b.queuedAt) > MAX_AGE || Date.parse(b.startsAt) <= Date.now()) {
          drop(b);
          changed = true;
          notify(
            `Rendez-vous non envoyé (${label(b)}) : la coupure a duré trop longtemps. Choisissez un nouveau créneau.`,
            false,
          );
          continue;
        }
        try {
          await api('/appointments', {
            method: 'POST',
            body: JSON.stringify({ slotId: b.slotId }),
            headers: b.measure ? { 'X-AB-Measure': '1' } : {},
          });
          drop(b);
          changed = true;
          notify(`Rendez-vous confirmé après la coupure : ${label(b)}.`, true);
        } catch (e) {
          if (!(e instanceof ApiError) || [502, 503, 504].includes(e.status)) break; // toujours injoignable : on garde la file
          if (e.status === 409) {
            // la première tentative a pu aboutir sans que la réponse arrive : on vérifie avant de conclure
            const mine = await api<Appointment[]>('/appointments').catch(() => [] as Appointment[]);
            if (
              currentClinic !== clinic.slug ||
              currentUser !== session.user?.id ||
              revision !== clinic.revision
            )
              break;
            drop(b);
            changed = true;
            if (mine.some((a) => a.slotId === b.slotId && a.status === 'CONFIRMED'))
              notify(`Rendez-vous confirmé : ${label(b)}.`, true);
            else
              notify(
                `Le créneau ${label(b)} a été pris pendant la coupure. Votre rendez-vous n’a pas été enregistré : choisissez un autre créneau.`,
                false,
              );
            continue;
          }
          if (e.status === 401) {
            onExpired();
            break;
          }
          b.attempts++;
          save();
          if (b.attempts >= MAX_ATTEMPTS) {
            drop(b);
            changed = true;
            notify(
              `Échec de l’enregistrement (${label(b)}) : ${e.message} Le rendez-vous n’a pas été réservé.`,
              false,
            );
          } else
            notify(
              `Problème d’enregistrement (${label(b)}) : ${e.message} Nouvelle tentative automatique.`,
              false,
            );
        }
      }
    } catch {
      /* service encore injoignable */
    } finally {
      flushing = false;
    }
    return changed;
  }
  return { pending, add, flush };
}
