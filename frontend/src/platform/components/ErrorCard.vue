<script setup lang="ts">
import PfIcon from './PfIcon.vue';
import PfLink from './PfLink.vue';
import type { Problem } from '../context';

// Même composant d'erreur partout : contexte, message du back, une seule action de sortie.
defineProps<{ problem: Problem }>();
const emit = defineEmits<{ relogin: [] }>();
const titles = { 401: 'Session expirée', 403: 'Accès refusé', 404: 'Introuvable' } as const;
const icons = { 401: 'clock', 403: 'shield', 404: 'search' } as const;
</script>
<template>
  <section class="pf-card pf-error" role="alert" aria-labelledby="error-title">
    <span class="pf-error-icon" :class="`pf-error-${problem.status}`"
      ><PfIcon :name="icons[problem.status]" :size="26"
    /></span>
    <span class="pf-tag pf-mono">{{ problem.status }}</span>
    <h1 id="error-title" class="pf-title">{{ titles[problem.status] }}</h1>
    <p class="pf-error-message">« {{ problem.message }} »</p>
    <button
      v-if="problem.status === 401"
      type="button"
      class="pf-button pf-primary"
      @click="emit('relogin')"
    >
      Se reconnecter
    </button>
    <a v-else-if="problem.status === 403" class="pf-button pf-primary" href="/"
      >Retour à mon cabinet</a
    >
    <PfLink v-else to="/platform/clinics" class="pf-button pf-primary">Liste des cabinets</PfLink>
  </section>
</template>
