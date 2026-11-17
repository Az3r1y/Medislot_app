<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue';
import { ApiError } from '../../api';
import { body, platformApi } from '../platformApi';
import { messageOf, useConsole } from '../context';
import PfIcon from './PfIcon.vue';
import type { ClinicRow } from '../types';

const props = defineProps<{ clinic: ClinicRow }>();
const emit = defineEmits<{ close: []; deleted: [clinic: ClinicRow] }>();
const { fail } = useConsole();
const confirmSlug = ref(''),
  password = ref(''),
  busy = ref(false),
  error = ref(''),
  fields = ref<Record<string, string>>({}),
  dialog = ref<HTMLElement | null>(null),
  first = ref<HTMLInputElement | null>(null);
let previous: Element | null = null;

// Le bouton reste désactivé tant que l'identifiant tapé ne correspond pas.
const matches = computed(() => confirmSlug.value === props.clinic.slug);

onMounted(async () => {
  previous = document.activeElement;
  await nextTick();
  first.value?.focus();
});
onBeforeUnmount(() => (previous as HTMLElement | null)?.focus?.());

function close() {
  if (!busy.value) emit('close');
}
// Focus maintenu dans la fenêtre (Tab et Maj+Tab).
function trap(event: KeyboardEvent) {
  if (event.key === 'Escape') return close();
  if (event.key !== 'Tab' || !dialog.value) return;
  const items = [
    ...dialog.value.querySelectorAll<HTMLElement>('input, button:not(:disabled), a[href]'),
  ];
  const firstItem = items[0],
    last = items[items.length - 1];
  if (event.shiftKey && document.activeElement === firstItem) {
    event.preventDefault();
    last?.focus();
  } else if (!event.shiftKey && document.activeElement === last) {
    event.preventDefault();
    firstItem?.focus();
  }
}

// DELETE avec le mot de passe ressaisi (nouvelle authentification), journalisé côté back.
async function submit() {
  if (!matches.value || busy.value) return;
  busy.value = true;
  error.value = '';
  fields.value = {};
  try {
    await platformApi(`/clinics/${props.clinic.slug}`, {
      method: 'DELETE',
      body: body({ confirmSlug: confirmSlug.value, password: password.value }),
    });
    emit('deleted', props.clinic);
  } catch (e) {
    if (e instanceof ApiError && Object.keys(e.fields).length) fields.value = e.fields;
    else if (!fail(e)) error.value = messageOf(e);
  } finally {
    busy.value = false;
  }
}
</script>
<template>
  <div class="pf-overlay" @click.self="close">
    <form
      ref="dialog"
      class="pf-dialog"
      role="dialog"
      aria-modal="true"
      aria-labelledby="delete-title"
      aria-describedby="delete-consequences"
      novalidate
      @keydown="trap"
      @submit.prevent="submit"
    >
      <div class="pf-dialog-body">
        <div class="pf-dialog-head">
          <span class="pf-danger-badge"><PfIcon name="trash" :size="22" /></span>
          <div>
            <h2 id="delete-title">Supprimer « {{ clinic.name }} » ?</h2>
            <p class="pf-muted">Cette action supprime définitivement :</p>
          </div>
        </div>
        <ul id="delete-consequences" class="pf-consequences">
          <li><PfIcon name="close" />le cabinet et sa configuration ;</li>
          <li>
            <PfIcon name="close" />ses <strong>{{ clinic.professionals }} praticiens</strong> ;
          </li>
          <li>
            <PfIcon name="close" />tous ses rendez-vous :
            <strong>{{ clinic.appointments30d }}</strong> sur les 30 derniers jours, plus
            l’historique.
          </li>
          <li class="pf-consequence-warning">
            <PfIcon name="alert" />Les patients ne pourront plus accéder à ces rendez-vous.
            <strong>Elle ne peut pas être annulée.</strong>
          </li>
        </ul>
        <p v-if="error" class="pf-alert pf-alert-error" role="alert">
          <PfIcon name="alert" /><span>{{ error }}</span>
        </p>
        <label class="pf-field">
          <span
            >Tapez <code class="pf-code">{{ clinic.slug }}</code> pour confirmer</span
          >
          <input
            ref="first"
            v-model="confirmSlug"
            class="pf-input pf-mono"
            autocomplete="off"
            spellcheck="false"
            :aria-invalid="!!fields.confirmSlug"
            aria-describedby="confirm-help"
          />
          <span v-if="fields.confirmSlug" id="confirm-help" class="pf-field-error">{{
            fields.confirmSlug
          }}</span>
          <span v-else-if="matches" id="confirm-help" class="pf-field-ok"
            ><PfIcon name="check" :size="14" />Identifiant correct</span
          >
          <span v-else id="confirm-help" class="pf-help"
            >Le bouton reste désactivé tant que l’identifiant ne correspond pas.</span
          >
        </label>
        <label class="pf-field">
          <span>Votre mot de passe master</span>
          <input
            v-model="password"
            class="pf-input"
            type="password"
            autocomplete="current-password"
            :aria-invalid="!!fields.password"
            :aria-describedby="fields.password ? 'password-error' : undefined"
          />
          <span v-if="fields.password" id="password-error" class="pf-field-error">{{
            fields.password
          }}</span>
        </label>
      </div>
      <div class="pf-dialog-foot">
        <button type="button" class="pf-button" :disabled="busy" @click="close">Annuler</button>
        <button class="pf-button pf-danger" :disabled="!matches || !password || busy">
          <PfIcon name="trash" />{{ busy ? 'Suppression…' : 'Supprimer définitivement' }}
        </button>
      </div>
    </form>
  </div>
</template>
