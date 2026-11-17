<script setup lang="ts">
import { ref, computed, onMounted, watch, nextTick, onBeforeUnmount } from 'vue';
import { storeToRefs } from 'pinia';
import { useClinic } from './stores/clinic';
import { useAgenda } from './stores/agenda';
import { clinicFromPath, clinicPath } from './tenant';
import { ClinicChangedError, clinicSnapshot } from './clinicRequest';
import ClinicSwitcher from './components/ClinicSwitcher.vue';
import ClinicAgenda from './components/ClinicAgenda.vue';
import AppHeader from './components/AppHeader.vue';
import SearchHero from './components/SearchHero.vue';
import SearchResults from './components/SearchResults.vue';
import AppointmentList from './components/AppointmentList.vue';
import NoticesPanel from './components/NoticesPanel.vue';
import AuthForm from './components/AuthForm.vue';
import BookingConfirm from './components/BookingConfirm.vue';
import ProfessionalProfile from './components/ProfessionalProfile.vue';
import AppointmentHistory from './components/AppointmentHistory.vue';
import CancelConfirm from './components/CancelConfirm.vue';
import { api, ApiError } from './api';
import { takeGoogleError } from './googleAuth';
import { useSession } from './stores/session';
import { useLive } from './composables/useLive';
import { useAb } from './composables/useAb';
import { usePendingBookings } from './composables/usePendingBookings';
import { useSearch } from './composables/useSearch';
import { useModalFocus } from './composables/useModalFocus';
import {
  dateLabel,
  timeLabel,
  type Professional,
  type Slot,
  type Appointment,
  type Notice,
} from './types';
const clinic = useClinic(),
  agenda = useAgenda(),
  session = useSession(),
  live = useLive(),
  personalLive = useLive(),
  ab = useAb(),
  pending = usePendingBookings(notify, () =>
    sessionExpired(
      'Votre session a expiré. Reconnectez-vous pour envoyer le rendez-vous en attente.',
    ),
  );
const online = ref(navigator.onLine);
const confirmLabel = computed(() =>
  moveId.value
    ? 'Confirmer le déplacement'
    : ab.variant.value === 'B'
      ? 'Réserver'
      : 'Confirmer le rendez-vous',
);
const view = ref<'search' | 'appointments'>('search'),
  busy = ref(false),
  error = ref(''),
  success = ref('');
const {
  q,
  city,
  specialty,
  page,
  total,
  loading,
  doctors,
  slots,
  near,
  search,
  loadSlots,
  searchNear,
  clearNear,
  resetSearch,
  goToPage,
  clear: clearSearch,
} = useSearch({ online, error, describe: (e) => message(e) });
const { appointments } = storeToRefs(agenda);
const notices = ref<Notice[]>([]),
  showNotices = ref(false),
  profile = ref<Professional | null>(null);
const authOpen = ref(false),
  register = ref(false),
  email = ref(''),
  password = ref(''),
  name = ref(''),
  website = ref(''),
  authError = ref('');
const chosen = ref<{ professional: Professional; slot: Slot } | null>(null),
  moveId = ref<number | null>(null),
  history = ref<{ action: string; createdAt: string }[] | null>(null),
  cancelId = ref<number | null>(null);
// Exposition A/B : le bouton de confirmation est affiché et utilisable.
watch([chosen, authOpen, online, busy], async () => {
  if (
    !chosen.value ||
    authOpen.value ||
    moveId.value ||
    busy.value ||
    !ab.enabled.value ||
    !online.value
  )
    return;
  await nextTick();
  if (modal.value?.querySelector('[data-ab-confirm]:not([aria-disabled="true"])'))
    await ab.expose();
});
// Quelle fenêtre est ouverte : sert au focus clavier.
const modalKind = computed(() =>
  authOpen.value
    ? 'auth'
    : chosen.value
      ? 'chosen'
      : profile.value
        ? 'profile'
        : history.value
          ? 'history'
          : cancelId.value
            ? 'cancel'
            : null,
);
const { modal, focusTitle, trap, refocusIfLost } = useModalFocus(view, modalKind, closeModal);
function closeModal() {
  if (busy.value) return;
  authOpen.value = false;
  chosen.value = null;
  profile.value = null;
  history.value = null;
  cancelId.value = null;
}
// réseau coupé (TypeError) ou back arrêté derrière nginx (502/503/504) : même mode hors ligne
const timedOut = (e: unknown) => e instanceof DOMException && e.name === 'TimeoutError';
const unreachable = (e: unknown) =>
  e instanceof TypeError ||
  timedOut(e) ||
  (e instanceof ApiError && [502, 503, 504].includes(e.status));
// après un délai dépassé, la requête a pu arriver au serveur : on ne conclut ni succès ni échec
// Session expirée : on garde l'écran et le créneau choisi, on redemande la connexion.
function sessionExpired(text = 'Votre session a expiré. Reconnectez-vous pour continuer.') {
  if (!session.user) return;
  session.user = null;
  clinic.afterLogout();
  register.value = false;
  authError.value = text;
  authOpen.value = true;
}
function message(e: unknown) {
  if (e instanceof ClinicChangedError) return '';
  if (unreachable(e)) online.value = false;
  if (e instanceof ApiError && e.status === 401 && session.user) {
    sessionExpired();
    return 'Votre session a expiré. Reconnectez-vous pour continuer.';
  }
  if (timedOut(e))
    return 'Le serveur ne répond pas. Au retour de la connexion, vérifiez « Mes rendez-vous » avant de réessayer.';
  return unreachable(e)
    ? 'Connexion impossible. Vérifiez le réseau puis réessayez.'
    : e instanceof Error
      ? e.message
      : 'Une erreur est survenue.';
}
async function refreshAppointments() {
  if (!session.user) return;
  await agenda.load(session.user.role === 'SECRETARY' || session.user.role === 'ADMIN');
}
async function refreshNotices() {
  if (session.user) notices.value = await api<Notice[]>('/notifications');
}
function choose(professional: Professional, slot: Slot) {
  success.value = '';
  error.value = '';
  profile.value = null;
  chosen.value = { professional, slot };
  if (!session.user) {
    authOpen.value = true;
    authError.value = '';
  }
}
// Erreurs par champ du 400 ; focus sur le premier champ invalide (RGAA 11.11).
const fieldErrors = ref<Record<string, string>>({});
async function authenticate() {
  if (busy.value) return;
  busy.value = true;
  authError.value = '';
  fieldErrors.value = {};
  try {
    if (register.value)
      await api('/register', {
        method: 'POST',
        body: JSON.stringify({
          name: name.value,
          email: email.value,
          password: password.value,
          website: website.value,
        }),
      });
    await session.login(email.value, password.value, website.value);
    authOpen.value = false;
    password.value = '';
    await refreshAppointments();
  } catch (e) {
    authError.value = message(e);
    if (e instanceof ApiError) fieldErrors.value = e.fields;
    await nextTick();
    modal.value?.querySelector<HTMLElement>('[aria-invalid="true"]')?.focus();
  } finally {
    busy.value = false;
  }
}
// notification dans la page et, onglet en arrière-plan, notification système
function notify(text: string, ok: boolean) {
  if (ok) success.value = text;
  else error.value = text;
  if ('Notification' in window && Notification.permission === 'granted' && document.hidden)
    new Notification('MediSlot', { body: text });
}
async function flushPending() {
  if (await pending.flush()) {
    await refreshAppointments().catch(() => {});
    await refreshNotices().catch(() => {});
    await loadSlots().catch(() => {});
  }
}
function queue() {
  const c = chosen.value!;
  pending.add({
    slotId: c.slot.id,
    professionalName: c.professional.name,
    startsAt: c.slot.startsAt,
    measure: ab.measure.value,
  });
  chosen.value = null;
  error.value = '';
  success.value =
    'Service momentanément indisponible : votre demande de rendez-vous est gardée sur cet appareil et sera envoyée automatiquement à son retour. Vous serez notifié du résultat.';
  if ('Notification' in window && Notification.permission === 'default')
    Notification.requestPermission().catch(() => {});
  if (online.value) setTimeout(flushPending, 10_000);
}
async function confirm() {
  if (!chosen.value || busy.value || (!online.value && moveId.value)) return;
  if (!online.value) {
    queue();
    return;
  }
  busy.value = true;
  error.value = '';
  try {
    await api(moveId.value ? `/appointments/${moveId.value}` : '/appointments', {
      method: moveId.value ? 'PATCH' : 'POST',
      body: JSON.stringify({ slotId: chosen.value.slot.id }),
      headers: !moveId.value && ab.measure.value ? { 'X-AB-Measure': '1' } : {},
    });
    success.value = moveId.value
      ? 'Votre rendez-vous a été déplacé.'
      : 'Votre rendez-vous est confirmé.';
    chosen.value = null;
    moveId.value = null;
    await refreshAppointments();
    await refreshNotices();
    await loadSlots();
    view.value = 'appointments';
  } catch (e) {
    if (!moveId.value && (unreachable(e) || (e instanceof ApiError && e.status >= 500))) {
      message(e);
      queue();
      return;
    }
    error.value = message(e);
    if (e instanceof ApiError && e.status === 409) {
      chosen.value = null;
      await loadSlots();
      await nextTick();
      refocusIfLost();
    }
  } finally {
    busy.value = false;
  }
}
async function cancel() {
  if (cancelId.value === null || busy.value || !online.value) return;
  busy.value = true;
  try {
    await api(`/appointments/${cancelId.value}/cancel`, { method: 'POST' });
    cancelId.value = null;
    success.value = 'Votre rendez-vous a été annulé.';
    await refreshAppointments();
    await loadSlots();
    await refreshNotices();
  } catch (e) {
    error.value = message(e);
  } finally {
    busy.value = false;
  }
}
async function move(a: Appointment) {
  moveId.value = a.id;
  q.value = a.professionalName;
  city.value = '';
  specialty.value = '';
  view.value = 'search';
  await search(true);
  success.value =
    'Choisissez un nouveau créneau. Votre réservation actuelle reste conservée jusqu’à confirmation.';
}
async function showHistory(id: number) {
  try {
    history.value = await api(`/appointments/${id}/history`);
    error.value = '';
  } catch (e) {
    error.value = message(e);
  }
}
async function logout() {
  try {
    await session.logout();
    personalLive.close();
    notices.value = [];
    appointments.value = [];
    showNotices.value = false;
    view.value = 'search';
    moveId.value = null;
  } catch (e) {
    error.value = message(e);
  }
}
function goHome() {
  view.value = 'search';
  moveId.value = null;
}
function abandonMove() {
  moveId.value = null;
  success.value = '';
}
function toggleAuthMode() {
  register.value = !register.value;
  fieldErrors.value = {};
}
function openAuth() {
  authOpen.value = true;
  authError.value = '';
  fieldErrors.value = {};
  register.value = false;
}
function myAppointments() {
  if (!session.user) {
    openAuth();
    return;
  }
  view.value = 'appointments';
  refreshAppointments().catch((e) => (error.value = message(e)));
}
watch(
  () => session.user,
  (user) => {
    personalLive.close();
    if (user) {
      flushPending();
      refreshNotices().catch(() => {});
      personalLive.connect(
        '/api/notifications/events',
        () => {
          refreshNotices().catch(() => {});
          refreshAppointments().catch(() => {});
        },
        'notifications-changed',
      );
    }
  },
);
function connectStreams() {
  live.connect('/api/events', () => loadSlots().catch(() => {}));
  personalLive.close();
  if (session.user)
    personalLive.connect(
      '/api/notifications/events',
      () => {
        refreshNotices().catch(() => {});
        refreshAppointments().catch(() => {});
      },
      'notifications-changed',
    );
}
watch(
  () => [clinic.slug, clinic.revision],
  () => {
    clearSearch();
    agenda.clear();
    notices.value = [];
    history.value = null;
    profile.value = null;
    chosen.value = null;
    moveId.value = null;
    cancelId.value = null;
    showNotices.value = false;
    success.value = '';
    error.value = '';
    live.close();
    personalLive.close();
    const snapshot = clinicSnapshot();
    nextTick().then(() => {
      if (!initialized || snapshot?.slug !== clinic.slug || snapshot?.revision !== clinic.revision)
        return;
      search(true).catch(() => {});
      refreshAppointments().catch((e) => (error.value = message(e)));
      refreshNotices().catch(() => {});
      connectStreams();
    });
  },
  { flush: 'sync' },
);
function navigateClinic() {
  const next = clinicFromPath(window.location.pathname);
  try {
    if (!next) throw new Error('Cabinet invalide.');
    clinic.switchTo(next, false);
  } catch (e) {
    window.history.replaceState(null, '', clinicPath(clinic.slug));
    error.value = message(e);
  }
}
window.addEventListener('popstate', navigateClinic);
onBeforeUnmount(() => window.removeEventListener('popstate', navigateClinic));
// la version reste celle reçue au chargement : pas de nouvelle affectation au retour du réseau
window.addEventListener('offline', () => {
  online.value = false;
});
// le retour du serveur ne déclenche aucun événement navigateur : on réessaie toutes les 10 s
let retry: ReturnType<typeof setInterval> | undefined;
watch(online, (v) => {
  clearInterval(retry);
  if (v) flushPending();
  else
    retry = setInterval(() => {
      search().catch(() => {});
    }, 10_000);
});
window.addEventListener('online', () => {
  online.value = true;
  error.value = '';
  search().catch(() => {});
  refreshAppointments().catch(() => {});
});
let initialized = false;
onMounted(async () => {
  const googleError = takeGoogleError(window.location.href);
  if (googleError) window.history.replaceState(window.history.state, '', googleError.cleanUrl);
  try {
    await clinic.load();
  } catch (e) {
    error.value = message(e);
  }
  try {
    await session.load();
  } catch (e) {
    error.value = message(e);
  }
  await session.loadProviders();
  if (googleError) {
    openAuth();
    authError.value = googleError.message;
  }
  initialized = true;
  ab.load();
  await search();
  connectStreams();
});
</script>
<template>
  <a href="#main" class="skip" @click.prevent="focusTitle">Aller au contenu</a>
  <AppHeader
    :view="view"
    :user="session.user"
    :notice-count="notices.length"
    :show-notices="showNotices"
    @home="goHome"
    @search="view = 'search'"
    @appointments="myAppointments"
    @toggle-notices="showNotices = !showNotices"
    @login="openAuth"
    @logout="logout"
  />
  <ClinicSwitcher :disabled="busy" @error="error = $event" />
  <main id="main">
    <SearchHero
      v-if="view === 'search'"
      v-model:q="q"
      v-model:specialty="specialty"
      v-model:city="city"
      :loading="loading"
      :near="near"
      :live-connected="live.connected.value"
      @submit="search(true)"
      @position="searchNear"
      @clear-near="clearNear"
    />
    <div class="main">
      <NoticesPanel
        v-if="showNotices && session.user"
        :notices="notices"
        @close="showNotices = false"
      />
      <!-- régions live toujours présentes : seul leur contenu apparaît, donc il est annoncé (RGAA 7.5) -->
      <div role="alert">
        <p v-if="!online" class="alert error">
          Hors ligne : le service MediSlot est injoignable (réseau coupé ou serveur arrêté). Les
          disponibilités affichées peuvent ne plus être à jour. Une nouvelle demande de rendez-vous
          est gardée sur cet appareil et envoyée à son retour ; déplacement et annulation sont
          suspendus. <button class="text-button" @click="search()">Réessayer</button>
        </p>
        <p v-if="error && online" class="alert error">
          {{ error }} <button class="text-button" @click="search()">Réessayer</button>
        </p>
      </div>
      <div role="status">
        <p v-if="pending.pending.value.length" class="move-banner">
          {{ pending.pending.value.length }} rendez-vous en attente d’envoi :
          {{
            pending.pending.value
              .map(
                (p) => `${p.professionalName}, ${dateLabel(p.startsAt)} à ${timeLabel(p.startsAt)}`,
              )
              .join(' · ')
          }}
        </p>
        <p v-if="success" class="alert success">{{ success }}</p>
      </div>
      <SearchResults
        v-if="view === 'search'"
        :total="total"
        :doctors="doctors"
        :slots="slots"
        :loading="loading"
        :near="near"
        :move-id="moveId"
        :page="page"
        @choose="choose"
        @profile="profile = $event"
        @reset="resetSearch"
        @abandon-move="abandonMove"
        @go-to-page="goToPage"
      />
      <ClinicAgenda
        v-else-if="session.user?.role === 'SECRETARY' || session.user?.role === 'ADMIN'"
        :appointments="appointments"
        :loading="agenda.loading"
      />
      <AppointmentList
        v-else
        :key="`${clinic.slug}:${clinic.revision}`"
        :appointments="appointments"
        @book="view = 'search'"
        @move="move"
        @cancel="cancelId = $event"
        @history="showHistory"
      />
    </div>
  </main>
  <footer>
    <span class="brand footer-brand">MediSlot.</span>
    <p>Votre parcours de soins, simplement.</p>
    <small>Application de formation · Données fictives · Horaires Europe/Paris</small>
  </footer>
  <div
    v-if="authOpen || chosen || profile || history || cancelId"
    class="overlay"
    @click.self="closeModal"
  >
    <section
      ref="modal"
      class="modal"
      role="dialog"
      aria-modal="true"
      aria-labelledby="modal-title"
      @keydown="trap"
    >
      <button class="close" aria-label="Fermer" @click="closeModal">×</button>
      <AuthForm
        v-if="authOpen"
        v-model:email="email"
        v-model:password="password"
        v-model:name="name"
        v-model:website="website"
        :register="register"
        :busy="busy"
        :google="session.providers.google"
        :auth-error="authError"
        :field-errors="fieldErrors"
        @submit="authenticate"
        @toggle-mode="toggleAuthMode"
      />
      <BookingConfirm
        v-else-if="chosen"
        :professional="chosen.professional"
        :slot="chosen.slot"
        :error="error"
        :label="confirmLabel"
        :ready="ab.ready.value"
        :disabled="busy || (!online && !!moveId)"
        :busy="busy"
        @confirm="confirm"
      />
      <ProfessionalProfile v-else-if="profile" :profile="profile" @close="profile = null" />
      <AppointmentHistory v-else-if="history" :history="history" />
      <CancelConfirm
        v-else-if="cancelId"
        :disabled="busy || !online"
        @confirm="cancel"
        @keep="cancelId = null"
      />
    </section>
  </div>
</template>
