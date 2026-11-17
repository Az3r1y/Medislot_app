<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue';
import { platformApi } from '../platformApi';
import { messageOf, useConsole } from '../context';
import {
  addDays,
  dayLabel,
  label,
  mondayOf,
  plainDate,
  statusLabels,
  timeLabel,
  today,
} from '../format';
import { specialties } from '../../types';
import PfIcon from '../components/PfIcon.vue';
import PfLink from '../components/PfLink.vue';
import type { ClinicDetail, PractitionerWeek } from '../types';

// Le master voit l'activité, pas la santé des patients : date, heure, statut et pseudonyme.
const props = defineProps<{ slug: string; practitioner: number }>();
const { fail } = useConsole();
const week = ref(mondayOf(today())),
  status = ref(''),
  data = ref<PractitionerWeek | null>(null),
  clinicName = ref(''),
  error = ref(''),
  loading = ref(false);

async function load() {
  loading.value = true;
  error.value = '';
  try {
    const params = new URLSearchParams({ week: week.value });
    if (status.value) params.set('status', status.value);
    data.value = await platformApi<PractitionerWeek>(
      `/clinics/${props.slug}/practitioners/${props.practitioner}/appointments?${params}`,
    );
  } catch (e) {
    if (!fail(e)) error.value = messageOf(e);
  } finally {
    loading.value = false;
  }
}
onMounted(async () => {
  void load();
  try {
    clinicName.value = (await platformApi<ClinicDetail>(`/clinics/${props.slug}`)).clinic.name;
  } catch {}
});
watch([week, status], load);

const counts = computed(() => {
  const c: Record<string, number> = { PAST: 0, CONFIRMED: 0, CANCELLED: 0 };
  for (const a of data.value?.appointments ?? []) c[a.status]++;
  return c;
});
// Pseudonyme déjà vu plus tôt dans la semaine : patient récurrent, jamais identifié.
const repeated = computed(() => {
  const seen = new Map<string, string>();
  const notes = new Map<number, string>();
  for (const a of data.value?.appointments ?? []) {
    const earlier = seen.get(a.patient);
    if (earlier) notes.set(a.id, `même patient que ${earlier}`);
    else seen.set(a.patient, `${dayLabel(a.startsAt)} ${timeLabel(a.startsAt)}`);
  }
  return notes;
});
const initials = computed(() =>
  (data.value?.practitioner.name ?? '')
    .replace(/^Dr\.?\s+/i, '')
    .split(/\s+/)
    .map((w) => w.charAt(0))
    .join('')
    .slice(0, 2),
);
const icon = { PAST: 'check', CONFIRMED: 'calendar', CANCELLED: 'ban' } as const;
</script>
<template>
  <div class="pf-page">
    <nav class="pf-crumb" aria-label="Fil d’Ariane">
      <PfLink to="/platform/clinics">Cabinets</PfLink>
      <PfIcon name="right" :size="14" />
      <PfLink :to="`/platform/clinics/${slug}`">{{ clinicName || slug }}</PfLink>
      <PfIcon name="right" :size="14" />
      <span aria-current="page">{{ data?.practitioner.name ?? 'Praticien' }}</span>
    </nav>
    <header class="pf-row pf-person-head">
      <span class="pf-avatar pf-avatar-lg" aria-hidden="true">{{ initials }}</span>
      <div>
        <h1 class="pf-title">Rendez-vous · {{ data?.practitioner.name ?? '…' }}</h1>
        <p class="pf-subtitle">
          {{
            data ? (specialties[data.practitioner.specialty] ?? data.practitioner.specialty) : ''
          }}
          <template v-if="clinicName"> · {{ clinicName }}</template>
        </p>
      </div>
    </header>
    <div class="pf-filters">
      <button
        type="button"
        class="pf-icon-button pf-bordered pf-round"
        aria-label="Semaine précédente"
        @click="week = addDays(week, -7)"
      >
        <PfIcon name="left" />
      </button>
      <label class="pf-select">
        <PfIcon name="calendar" />
        <span>Semaine du</span>
        <input
          type="date"
          :value="week"
          @change="week = mondayOf(($event.target as HTMLInputElement).value || today())"
        />
      </label>
      <button
        type="button"
        class="pf-icon-button pf-bordered pf-round"
        aria-label="Semaine suivante"
        @click="week = addDays(week, 7)"
      >
        <PfIcon name="right" />
      </button>
      <label class="pf-select">
        <span>Statut</span>
        <select v-model="status">
          <option value="">tous</option>
          <option v-for="(text, key) in statusLabels" :key="key" :value="key">{{ text }}</option>
        </select>
      </label>
      <ul class="pf-counts" aria-label="Résumé de la semaine">
        <li><span class="pf-dot pf-dot-past" />{{ counts.PAST }} passé(s)</li>
        <li><span class="pf-dot pf-dot-confirmed" />{{ counts.CONFIRMED }} confirmé(s)</li>
        <li><span class="pf-dot pf-dot-cancelled" />{{ counts.CANCELLED }} annulé(s)</li>
      </ul>
    </div>
    <p class="pf-alert pf-alert-info">
      <PfIcon name="eyeOff" />
      <span
        ><strong>Vue minimisée : les patients sont pseudonymisés.</strong> Pour toute question sur
        un patient, s’adresser au cabinet.</span
      >
    </p>
    <p v-if="error" class="pf-alert pf-alert-error" role="alert">
      <PfIcon name="alert" /><span>{{ error }}</span>
    </p>
    <div class="pf-card pf-table-wrap" :aria-busy="loading">
      <table class="pf-table">
        <caption class="pf-visually-hidden">
          Rendez-vous de la semaine du
          {{
            plainDate(week)
          }}
        </caption>
        <thead>
          <tr>
            <th scope="col">Date</th>
            <th scope="col">Heure</th>
            <th scope="col">Patient (pseudonyme)</th>
            <th scope="col">Statut</th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="data && !data.appointments.length">
            <td colspan="4" class="pf-empty-cell">Aucun rendez-vous cette semaine.</td>
          </tr>
          <tr v-for="a in data?.appointments ?? []" :key="a.id">
            <th scope="row" class="pf-strong">{{ dayLabel(a.startsAt) }}</th>
            <td class="pf-num">{{ timeLabel(a.startsAt) }}</td>
            <td>
              <span class="pf-row"
                ><span class="pf-pseudo">{{ a.patient }}</span
                ><span v-if="repeated.get(a.id)" class="pf-small pf-muted">{{
                  repeated.get(a.id)
                }}</span></span
              >
            </td>
            <td>
              <span class="pf-status" :class="`pf-status-${a.status.toLowerCase()}`"
                ><PfIcon :name="icon[a.status]" :size="14" />{{
                  label(statusLabels, a.status)
                }}</span
              >
            </td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>
