<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue';
import { platformApi } from '../platformApi';
import { messageOf, useConsole } from '../context';
import {
  actionLabels,
  addDays,
  auditQuery,
  dateLabel,
  label,
  roleLabels,
  shortDate,
  timeLabel,
  today,
  type AuditFilters,
} from '../format';
import PfIcon from '../components/PfIcon.vue';
import PfLink from '../components/PfLink.vue';
import ClinicTabs from '../components/ClinicTabs.vue';
import type { AuditEntry, AuditPage, ClinicDetail } from '../types';

// Qui a fait quoi, quand, dans ce cabinet. Les actions du master y apparaissent aussi.
const props = defineProps<{ slug: string }>();
const { fail } = useConsole();
const filters = reactive<AuditFilters>({
  action: '',
  role: '',
  from: addDays(today(), -16),
  to: today(),
});
const name = ref(''),
  items = ref<AuditEntry[]>([]),
  total = ref(0),
  pages = ref<(string | null)[]>([null]),
  index = ref(0),
  next = ref<string | null>(null),
  error = ref(''),
  loading = ref(true);

// GET /api/platform/clinics/{id}/audit, paginé côté back (curseur).
async function load() {
  loading.value = true;
  error.value = '';
  try {
    const page = await platformApi<AuditPage>(
      `/clinics/${props.slug}/audit${auditQuery(filters, pages.value[index.value])}`,
    );
    items.value = page.items;
    total.value = page.total;
    next.value = page.nextCursor;
  } catch (e) {
    if (!fail(e)) error.value = messageOf(e);
  } finally {
    loading.value = false;
  }
}
onMounted(async () => {
  load();
  try {
    name.value = (await platformApi<ClinicDetail>(`/clinics/${props.slug}`)).clinic.name;
  } catch {}
});
watch(
  () => ({ ...filters }),
  () => {
    pages.value = [null];
    index.value = 0;
    load();
  },
);
function go(step: 1 | -1) {
  if (step === 1 && next.value) pages.value[index.value + 1] = next.value;
  index.value += step;
  load();
}
const first = computed(() => (total.value ? index.value * 25 + 1 : 0));
const last = computed(() => index.value * 25 + items.value.length);
// L'export reprend exactement les filtres affichés.
const exportUrl = computed(
  () => `/api/platform/clinics/${props.slug}/audit.csv${auditQuery(filters)}`,
);
const actions = Object.entries(actionLabels).filter(([k]) => k !== 'AUDIT_VIEWED' && k !== 'LOGIN');
</script>
<template>
  <div class="pf-page">
    <nav class="pf-crumb" aria-label="Fil d’Ariane">
      <PfLink to="/platform/clinics">Cabinets</PfLink>
      <PfIcon name="right" :size="14" />
      <PfLink :to="`/platform/clinics/${slug}`">{{ name || slug }}</PfLink>
      <PfIcon name="right" :size="14" />
      <span aria-current="page">Journal</span>
    </nav>
    <ClinicTabs :slug="slug" current="journal" />
    <header class="pf-page-head">
      <div class="pf-row">
        <h1 class="pf-title">Journal · {{ name || slug }}</h1>
        <span class="pf-tag"><PfIcon name="lock" :size="13" />Ajout seul</span>
      </div>
      <a class="pf-button" :href="exportUrl" download><PfIcon name="download" />Exporter CSV</a>
    </header>
    <div class="pf-filters">
      <label class="pf-select">
        <span>Action</span>
        <select v-model="filters.action">
          <option value="">toutes</option>
          <option v-for="[key, text] in actions" :key="key" :value="key">{{ text }}</option>
        </select>
      </label>
      <label class="pf-select">
        <span>Auteur</span>
        <select v-model="filters.role">
          <option value="">tous</option>
          <option v-for="(text, key) in roleLabels" :key="key" :value="key">{{ text }}</option>
        </select>
      </label>
      <label class="pf-select"
        ><PfIcon name="calendar" /><span>Du</span
        ><input v-model="filters.from" type="date" :max="filters.to"
      /></label>
      <label class="pf-select"
        ><span>Au</span><input v-model="filters.to" type="date" :min="filters.from"
      /></label>
    </div>
    <p v-if="error" class="pf-alert pf-alert-error" role="alert">
      <PfIcon name="alert" /><span>{{ error }}</span>
    </p>
    <div class="pf-card pf-table-wrap" :aria-busy="loading">
      <table class="pf-table">
        <caption class="pf-visually-hidden">
          Journal du cabinet, du plus récent au plus ancien
        </caption>
        <thead>
          <tr>
            <th scope="col">Date et heure</th>
            <th scope="col">Auteur</th>
            <th scope="col">Rôle</th>
            <th scope="col">Action</th>
            <th scope="col">Objet</th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="!loading && !items.length">
            <td colspan="5" class="pf-empty-cell">Aucune action sur cette période.</td>
          </tr>
          <tr v-for="e in items" :key="e.id" :class="{ 'pf-row-master': e.platform }">
            <td>
              <span class="pf-stack"
                ><strong class="pf-num" :title="dateLabel(e.at)">{{ shortDate(e.at) }}</strong
                ><span class="pf-muted pf-num">{{ timeLabel(e.at) }}</span></span
              >
            </td>
            <td :class="{ 'pf-mono': e.role === 'PATIENT', 'pf-strong': e.platform }">
              {{ e.actor }}
            </td>
            <td>
              <span class="pf-tag" :class="{ 'pf-tag-master': e.platform }"
                ><PfIcon v-if="e.platform" name="shield" :size="12" />{{
                  label(roleLabels, e.role)
                }}</span
              >
            </td>
            <td>{{ label(actionLabels, e.action) }}</td>
            <td :class="{ 'pf-mono': e.target?.startsWith('RDV') }">{{ e.target ?? '—' }}</td>
          </tr>
        </tbody>
      </table>
      <div class="pf-pagination">
        <span class="pf-num">{{ first }} à {{ last }} sur {{ total.toLocaleString('fr-FR') }}</span>
        <span class="pf-row">
          <button
            type="button"
            class="pf-button pf-small-button"
            :disabled="index === 0 || loading"
            @click="go(-1)"
          >
            <PfIcon name="left" :size="14" />Précédent
          </button>
          <button
            type="button"
            class="pf-button pf-small-button"
            :disabled="!next || loading"
            @click="go(1)"
          >
            Suivant<PfIcon name="right" :size="14" />
          </button>
        </span>
      </div>
    </div>
    <p class="pf-note">
      <PfIcon name="shield" />Le journal ne contient jamais de motif de consultation ni de donnée de
      santé : seulement l’action et un identifiant d’objet. Votre consultation est elle-même
      journalisée.
    </p>
  </div>
</template>
