<script setup lang="ts">
import { computed, onBeforeUnmount, ref } from 'vue';
import { ApiError } from '../../api';
import { body, platformApi, refreshPlatformCsrf } from '../platformApi';
import { messageOf } from '../context';
import PfIcon from '../components/PfIcon.vue';
import type { Master } from '../types';

const emit = defineEmits<{ logged: [master: Master] }>();
const email = ref(''),
  password = ref(''),
  visible = ref(false),
  busy = ref(false),
  error = ref(''),
  lockedUntil = ref<number | null>(null),
  now = ref(Date.now());
let timer: ReturnType<typeof setInterval> | undefined;

const remaining = computed(() =>
  lockedUntil.value ? Math.max(0, Math.ceil((lockedUntil.value - now.value) / 1000)) : 0,
);
const countdown = computed(
  () =>
    `${String(Math.floor(remaining.value / 60)).padStart(2, '0')}:${String(remaining.value % 60).padStart(2, '0')}`,
);
const locked = computed(() => remaining.value > 0);

// Le front affiche le message du back tel quel et désactive le bouton pendant le délai.
function lock() {
  lockedUntil.value = Date.now() + 15 * 60 * 1000;
  clearInterval(timer);
  timer = setInterval(() => {
    now.value = Date.now();
    if (!locked.value) {
      clearInterval(timer);
      lockedUntil.value = null;
      error.value = '';
    }
  }, 1000);
}
onBeforeUnmount(() => clearInterval(timer));

async function submit() {
  if (busy.value || locked.value) return;
  error.value = '';
  busy.value = true;
  try {
    await refreshPlatformCsrf();
    const master = await platformApi<Master>('/login', {
      method: 'POST',
      body: body({ email: email.value, password: password.value }),
    });
    // Nouvel identifiant de session : nouveau jeton CSRF.
    await refreshPlatformCsrf();
    password.value = '';
    emit('logged', master);
  } catch (e) {
    error.value = messageOf(e);
    if (e instanceof ApiError && e.status === 429) lock();
  } finally {
    busy.value = false;
  }
}
</script>
<template>
  <main id="contenu" class="pf-login">
    <form class="pf-login-card" novalidate @submit.prevent="submit">
      <span class="pf-login-badge"><PfIcon name="lock" :size="22" /></span>
      <h1>Console master</h1>
      <p class="pf-muted">Accès réservé à l’équipe plateforme.</p>
      <p v-if="error" class="pf-alert pf-alert-error" role="alert">
        <PfIcon name="alert" />
        <span>{{ error }}</span>
      </p>
      <label class="pf-field">
        <span>Adresse e-mail</span>
        <input
          v-model="email"
          class="pf-input"
          type="email"
          autocomplete="username"
          required
          :disabled="locked"
        />
      </label>
      <label class="pf-field">
        <span>Mot de passe</span>
        <span class="pf-password">
          <input
            v-model="password"
            class="pf-input"
            :type="visible ? 'text' : 'password'"
            autocomplete="current-password"
            required
            :disabled="locked"
          />
          <button
            type="button"
            class="pf-icon-button"
            :aria-label="visible ? 'Masquer le mot de passe' : 'Afficher le mot de passe'"
            :aria-pressed="visible"
            @click="visible = !visible"
          >
            <PfIcon :name="visible ? 'eyeOff' : 'eye'" />
          </button>
        </span>
      </label>
      <button class="pf-button pf-primary pf-wide" :disabled="busy || locked">
        <template v-if="locked"><PfIcon name="clock" />Réessayer dans {{ countdown }}</template>
        <template v-else>{{ busy ? 'Connexion…' : 'Continuer' }}</template>
      </button>
      <p class="pf-small pf-muted">
        Patients et cabinets : connectez-vous depuis l’espace de votre cabinet.
      </p>
    </form>
  </main>
</template>
