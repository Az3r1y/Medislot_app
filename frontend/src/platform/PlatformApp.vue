<script setup lang="ts">
import { computed, onMounted, provide, ref, watch, type Ref } from 'vue';
import { ApiError } from '../api';
import { platformApi, platformLogout } from './platformApi';
import { CONSOLE, problemOf, type Problem } from './context';
import { navigate, route } from './router';
import PfIcon from './components/PfIcon.vue';
import PfLink from './components/PfLink.vue';
import ErrorCard from './components/ErrorCard.vue';
import PlatformLogin from './views/PlatformLogin.vue';
import ClinicList from './views/ClinicList.vue';
import ClinicForm from './views/ClinicForm.vue';
import ClinicDetailView from './views/ClinicDetailView.vue';
import ClinicJournal from './views/ClinicJournal.vue';
import PractitionerAppointments from './views/PractitionerAppointments.vue';
import PlatformDashboard from './views/PlatformDashboard.vue';
import MasterJournal from './views/MasterJournal.vue';
import type { Master } from './types';
import './platform.css';

// undefined : session en cours de vérification ; null : non connecté.
const master = ref<Master | null | undefined>(undefined);
const problem = ref<Problem | null>(null),
  toast = ref(''),
  startupError = ref('');
let toastTimer: ReturnType<typeof setTimeout> | undefined;

function fail(error: unknown): boolean {
  const p = problemOf(error);
  if (!p) return false;
  problem.value = p;
  return true;
}
function notify(message: string) {
  toast.value = message;
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => (toast.value = ''), 6000);
}
provide(CONSOLE, { master: master as Ref<Master | null>, fail, notify });

onMounted(async () => {
  document.title = 'Console master · MediSlot';
  try {
    master.value = await platformApi<Master>('/me');
    if (route.value.name === 'login') navigate('/platform/clinics', true);
  } catch (e) {
    if (e instanceof ApiError && (e.status === 401 || e.status === 403)) {
      master.value = null;
      // Un compte de cabinet connecté reçoit le même écran de refus que sur les autres pages.
      if (e.status === 403) fail(e);
    } else {
      master.value = null;
      startupError.value = 'Le service est momentanément indisponible.';
    }
  }
});
watch(route, () => (problem.value = null));

function logged(m: Master) {
  master.value = m;
  problem.value = null;
  startupError.value = '';
  if (route.value.name === 'login' || route.value.name === 'not-found')
    navigate('/platform/clinics', true);
}
function relogin() {
  problem.value = null;
  master.value = null;
}
async function logout() {
  try {
    await platformLogout();
  } finally {
    master.value = null;
    navigate('/platform/login', true);
  }
}

const section = computed(() =>
  route.value.name === 'dashboard'
    ? 'dashboard'
    : route.value.name === 'master-journal'
      ? 'journal'
      : 'clinics',
);
const showLogin = computed(
  () => master.value === null && (problem.value === null || problem.value.status === 401),
);
</script>
<template>
  <div class="pf-app">
    <a class="pf-skip" href="#contenu">Aller au contenu</a>
    <header class="pf-top">
      <span class="pf-brand-group">
        <span class="pf-brand">MediSlot<span class="pf-brand-dot">.</span></span>
        <span class="pf-zone"><PfIcon name="shield" :size="12" />Zone plateforme</span>
      </span>
      <span v-if="master" class="pf-account">
        <span class="pf-account-badge" aria-hidden="true">{{
          master.email.charAt(0).toUpperCase()
        }}</span>
        <span class="pf-account-email">{{ master.email }}</span>
        <button type="button" class="pf-top-link" @click="logout">
          <PfIcon name="logout" :size="16" />Déconnexion
        </button>
      </span>
      <span v-else class="pf-top-hint">Patients et cabinets : utilisez votre espace habituel</span>
    </header>

    <p v-if="startupError" class="pf-alert pf-alert-error pf-startup" role="alert">
      <PfIcon name="alert" /><span>{{ startupError }}</span>
    </p>

    <div v-if="master === undefined" class="pf-loading" aria-busy="true">
      Vérification de la session…
    </div>

    <main v-else-if="problem && !master && problem.status !== 401" id="contenu" class="pf-login">
      <ErrorCard :problem="problem" @relogin="relogin" />
    </main>

    <PlatformLogin v-else-if="showLogin" @logged="logged" />

    <div v-else class="pf-shell">
      <nav class="pf-side" aria-label="Console master">
        <span class="pf-side-title">Console master</span>
        <PfLink
          to="/platform/dashboard"
          class="pf-nav"
          :class="{ 'pf-nav-on': section === 'dashboard' }"
          :aria-current="section === 'dashboard' ? 'page' : undefined"
          ><PfIcon name="dashboard" />Dashboard</PfLink
        >
        <PfLink
          to="/platform/clinics"
          class="pf-nav"
          :class="{ 'pf-nav-on': section === 'clinics' }"
          :aria-current="section === 'clinics' ? 'page' : undefined"
          ><PfIcon name="building" />Cabinets</PfLink
        >
        <PfLink
          to="/platform/journal"
          class="pf-nav"
          :class="{ 'pf-nav-on': section === 'journal' }"
          :aria-current="section === 'journal' ? 'page' : undefined"
          ><PfIcon name="list" />Journal master</PfLink
        >
        <p class="pf-session">
          <PfIcon name="clock" :size="16" />Session courte : déconnexion après 15 min d’inactivité.
        </p>
      </nav>
      <main id="contenu" class="pf-main" tabindex="-1">
        <p v-if="toast" class="pf-alert pf-alert-success" role="status">
          <PfIcon name="check" /><span>{{ toast }}</span>
        </p>
        <ErrorCard v-if="problem" :problem="problem" @relogin="relogin" />
        <template v-else>
          <ClinicList v-if="route.name === 'clinics'" />
          <ClinicForm v-else-if="route.name === 'new'" :key="'new'" />
          <ClinicForm
            v-else-if="route.name === 'edit'"
            :key="`edit-${route.slug}`"
            :slug="route.slug"
          />
          <ClinicDetailView
            v-else-if="route.name === 'clinic'"
            :key="`clinic-${route.slug}`"
            :slug="route.slug"
          />
          <ClinicJournal
            v-else-if="route.name === 'journal'"
            :key="`journal-${route.slug}`"
            :slug="route.slug"
          />
          <PractitionerAppointments
            v-else-if="route.name === 'appointments'"
            :key="`rdv-${route.slug}-${route.practitioner}`"
            :slug="route.slug"
            :practitioner="route.practitioner"
          />
          <PlatformDashboard v-else-if="route.name === 'dashboard'" />
          <MasterJournal v-else-if="route.name === 'master-journal'" />
          <ErrorCard
            v-else
            :problem="{ status: 404, message: 'Cette page de la console n’existe pas.' }"
          />
        </template>
      </main>
    </div>
  </div>
</template>
