import { ref } from 'vue';
import { api, ApiError, refreshCsrf } from '../api';
import { dateLabel, timeLabel, type Appointment } from '../types';
export interface PendingBooking {
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
    return JSON.parse(localStorage.getItem(KEY) || '[]');
  } catch {
    return [];
  }
};
// réservations faites pendant une coupure : gardées sur l'appareil puis rejouées au retour du service
export function usePendingBookings(
  notify: (text: string, ok: boolean) => void,
  onExpired: () => void,
) {
  const pending = ref<PendingBooking[]>(read());
  let flushing = false;
  const save = () => {
    try {
      localStorage.setItem(KEY, JSON.stringify(pending.value));
    } catch {}
  };
  const drop = (slotId: number) => {
    pending.value = pending.value.filter((p) => p.slotId !== slotId);
    save();
  };
  const label = (b: PendingBooking) =>
    `${b.professionalName}, ${dateLabel(b.startsAt)} à ${timeLabel(b.startsAt)}`;
  function add(b: Omit<PendingBooking, 'queuedAt' | 'attempts'>) {
    if (!pending.value.some((p) => p.slotId === b.slotId))
      pending.value.push({ ...b, queuedAt: new Date().toISOString(), attempts: 0 });
    save();
  }
  /** Rejoue la file ; renvoie true si au moins une réservation a été tranchée (confirmée ou abandonnée). */
  async function flush() {
    if (flushing || !pending.value.length) return false;
    flushing = true;
    let changed = false;
    try {
      await refreshCsrf(); // le jeton a pu expirer pendant la coupure
      for (const b of [...pending.value]) {
        if (Date.now() - Date.parse(b.queuedAt) > MAX_AGE || Date.parse(b.startsAt) <= Date.now()) {
          drop(b.slotId);
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
          drop(b.slotId);
          changed = true;
          notify(`Rendez-vous confirmé après la coupure : ${label(b)}.`, true);
        } catch (e) {
          if (!(e instanceof ApiError) || [502, 503, 504].includes(e.status)) break; // toujours injoignable : on garde la file
          if (e.status === 409) {
            // la première tentative a pu aboutir sans que la réponse arrive : on vérifie avant de conclure
            const mine = await api<Appointment[]>('/appointments').catch(() => [] as Appointment[]);
            drop(b.slotId);
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
            drop(b.slotId);
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
