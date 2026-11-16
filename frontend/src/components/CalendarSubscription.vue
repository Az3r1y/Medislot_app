<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { api } from '../api';
import { useClinic } from '../stores/clinic';
const clinic = useClinic();

const active = ref(false);
const link = ref('');
const busy = ref(false);
const loaded = ref(false);
const message = ref('');
const error = ref('');

onMounted(async () => {
  try {
    active.value = (await api<{ active: boolean }>('/calendar/subscription')).active;
    loaded.value = true;
  } catch {
    error.value = 'Impossible de vérifier votre abonnement calendrier.';
  }
});

async function create() {
  if (active.value && !window.confirm('L’ancien lien cessera de fonctionner. Régénérer le lien ?'))
    return;
  busy.value = true;
  error.value = '';
  message.value = '';
  try {
    const { token } = await api<{ token: string }>('/calendar/subscription', { method: 'POST' });
    const url = new URL('/api/calendar/feed.ics', window.location.origin);
    url.searchParams.set('token', token);
    url.searchParams.set('clinic', clinic.slug);
    link.value = `webcal://${url.host}${url.pathname}${url.search}`;
    active.value = true;
    message.value =
      'Votre lien est prêt. Copiez-le maintenant, il ne sera plus affiché après avoir quitté cette page.';
  } catch {
    loaded.value = false;
    error.value =
      'Impossible de confirmer la création du lien. Rechargez la page pour vérifier son état.';
  } finally {
    busy.value = false;
  }
}

async function revoke() {
  busy.value = true;
  error.value = '';
  message.value = '';
  try {
    await api('/calendar/subscription', { method: 'DELETE' });
    active.value = false;
    link.value = '';
    message.value = 'Votre lien d’abonnement a été révoqué.';
  } catch {
    loaded.value = false;
    error.value =
      'Impossible de confirmer la révocation. Rechargez la page pour vérifier son état.';
  } finally {
    busy.value = false;
  }
}

async function copy() {
  try {
    await navigator.clipboard.writeText(link.value);
    message.value = 'Lien copié.';
    error.value = '';
  } catch {
    error.value = 'Impossible de copier le lien. Sélectionnez-le pour le copier manuellement.';
  }
}
</script>

<template>
  <section class="appointment" aria-labelledby="calendar-title">
    <h2 id="calendar-title">Abonnement calendrier</h2>
    <p>Votre agenda mettra les rendez-vous à jour selon sa fréquence de synchronisation.</p>
    <p v-if="active && !link">Un lien est actif. Régénérez-le pour obtenir un nouveau lien.</p>
    <div v-if="link">
      <label for="calendar-link">Lien personnel</label>
      <input
        id="calendar-link"
        :value="link"
        readonly
        @focus="($event.target as HTMLInputElement).select()"
      />
      <button @click="copy">Copier le lien</button>
      <a :href="link">Ouvrir dans mon agenda</a>
    </div>
    <button :disabled="busy || !loaded" @click="create">
      {{ active ? 'Régénérer le lien' : 'Créer un lien' }}
    </button>
    <button v-if="active" :disabled="busy || !loaded" @click="revoke">Révoquer le lien</button>
    <p role="status">{{ message }}</p>
    <p role="alert">{{ error }}</p>
  </section>
</template>
