<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue';
import { platformApi } from '../platformApi';
import { messageOf, useConsole } from '../context';
import { PERIODS, percent, period, plainDate, SLUG } from '../format';
import { navigate, search } from '../router';
import PfIcon from '../components/PfIcon.vue';
import PfLink from '../components/PfLink.vue';
import type { ClinicRow, Stats } from '../types';

const { fail } = useConsole();
const stats = ref<Stats | null>(null),
  clinics = ref<ClinicRow[]>([]),
  error = ref(''),
  loading = ref(false);
const DAY = /^\d{4}-\d{2}-\d{2}$/;

// La période et le cabinet choisis sont reflétés dans l'URL, pour partager ou recharger la vue.
const query = computed(() => {
  const p = new URLSearchParams(search.value);
  const fallback = period(30);
  const from = p.get('from') ?? '',
    to = p.get('to') ?? '';
  const valid = DAY.test(from) && DAY.test(to) && from <= to;
  const clinic = p.get('clinic') ?? 'all';
  return {
    from: valid ? from : fallback.from,
    to: valid ? to : fallback.to,
    clinic: clinic === 'all' || SLUG.test(clinic) ? clinic : 'all',
  };
});
const preset = computed(() => {
  const match = PERIODS.find((p) => {
    const r = period(p.days);
    return r.from === query.value.from && r.to === query.value.to;
  });
  return match ? String(match.days) : 'custom';
});
const shareUrl = computed(
  () =>
    `?from=${query.value.from}&to=${query.value.to}&clinic=${encodeURIComponent(query.value.clinic)}`,
);

function update(changes: Partial<{ from: string; to: string; clinic: string }>) {
  const next = { ...query.value, ...changes };
  navigate(
    `/platform/dashboard?from=${next.from}&to=${next.to}&clinic=${encodeURIComponent(next.clinic)}`,
    true,
  );
}
function choosePreset(value: string) {
  if (value !== 'custom') update(period(Number(value)));
}

// GET /api/platform/stats?from=…&to=…&clinic=… : uniquement des comptes, calculés côté back.
async function load() {
  loading.value = true;
  error.value = '';
  try {
    const q = query.value;
    stats.value = await platformApi<Stats>(
      `/stats?from=${q.from}&to=${q.to}&clinic=${encodeURIComponent(q.clinic)}`,
    );
  } catch (e) {
    if (!fail(e)) error.value = messageOf(e);
  } finally {
    loading.value = false;
  }
}
onMounted(async () => {
  // Sans période dans l'URL : on l'y inscrit, le changement d'URL déclenche le chargement.
  if (!new URLSearchParams(search.value).get('from')) update({});
  else void load();
  try {
    clinics.value = await platformApi<ClinicRow[]>('/clinics');
  } catch (e) {
    fail(e);
  }
});
watch(query, load);

const maxWeek = computed(() =>
  Math.max(1, ...(stats.value?.perWeek ?? []).map((w) => w.appointments)),
);
const weekLabel = (w: Stats['perWeek'][number]) =>
  w.start === w.end ? plainDate(w.start) : `${plainDate(w.start)}–${plainDate(w.end)}`;
const chartLabel = computed(
  () =>
    'Rendez-vous par semaine : ' +
    (stats.value?.perWeek ?? [])
      .map((w) => `${weekLabel(w)} : ${w.appointments}${w.partial ? ' (semaine incomplète)' : ''}`)
      .join(', '),
);
const totalProfessionals = computed(() =>
  (stats.value?.perClinic ?? []).reduce((s, c) => s + c.professionals, 0),
);
</script>
<template>
  <div class="pf-page">
    <header class="pf-page-head">
      <div>
        <h1 class="pf-title">Vue d’ensemble</h1>
        <p class="pf-subtitle">
          Du {{ plainDate(query.from) }} au {{ plainDate(query.to) }} · jours comptés en heure de
          Paris
        </p>
      </div>
    </header>
    <div class="pf-filters">
      <label class="pf-select">
        <PfIcon name="calendar" />
        <span>Période</span>
        <select :value="preset" @change="choosePreset(($event.target as HTMLSelectElement).value)">
          <option v-for="p in PERIODS" :key="p.days" :value="String(p.days)">{{ p.label }}</option>
          <option value="custom">Personnalisée</option>
        </select>
      </label>
      <template v-if="preset === 'custom'">
        <label class="pf-select"
          ><span>Du</span
          ><input
            type="date"
            :value="query.from"
            :max="query.to"
            @change="update({ from: ($event.target as HTMLInputElement).value })"
        /></label>
        <label class="pf-select"
          ><span>Au</span
          ><input
            type="date"
            :value="query.to"
            :min="query.from"
            @change="update({ to: ($event.target as HTMLInputElement).value })"
        /></label>
      </template>
      <label class="pf-select">
        <span>Cabinet</span>
        <select
          :value="query.clinic"
          @change="update({ clinic: ($event.target as HTMLSelectElement).value })"
        >
          <option value="all">tous</option>
          <option v-for="c in clinics" :key="c.slug" :value="c.slug">{{ c.name }}</option>
        </select>
      </label>
      <span class="pf-url" title="Adresse partageable de cette vue"
        ><PfIcon name="link" :size="14" /><span class="pf-mono">{{ shareUrl }}</span></span
      >
    </div>
    <p v-if="error" class="pf-alert pf-alert-error" role="alert">
      <PfIcon name="alert" /><span>{{ error }}</span>
      <button type="button" class="pf-link-button" @click="load">Réessayer</button>
    </p>
    <template v-else-if="stats">
      <div class="pf-kpis" :aria-busy="loading">
        <div class="pf-card pf-kpi">
          <span class="pf-kpi-label"
            ><span class="pf-kpi-icon pf-tint-blue"><PfIcon name="building" /></span>Cabinets</span
          >
          <span class="pf-kpi-value">{{ stats.clinics }}</span>
          <span class="pf-kpi-caption">{{
            query.clinic === 'all' ? 'sur la plateforme' : 'sélectionné'
          }}</span>
        </div>
        <div class="pf-card pf-kpi">
          <span class="pf-kpi-label"
            ><span class="pf-kpi-icon pf-tint-teal"><PfIcon name="user" /></span>Praticiens</span
          >
          <span class="pf-kpi-value">{{ stats.professionals }}</span>
          <span class="pf-kpi-caption">{{
            query.clinic === 'all' ? 'tous cabinets confondus' : 'dans ce cabinet'
          }}</span>
        </div>
        <div class="pf-card pf-kpi">
          <span class="pf-kpi-label"
            ><span class="pf-kpi-icon pf-tint-orange"><PfIcon name="calendar" /></span
            >Rendez-vous</span
          >
          <span class="pf-kpi-value">{{ stats.appointments }}</span>
          <span class="pf-kpi-caption">réservés sur la période</span>
        </div>
        <div class="pf-card pf-kpi">
          <span class="pf-kpi-label"
            ><span class="pf-kpi-icon pf-tint-red"><PfIcon name="ban" /></span>dont annulés</span
          >
          <span class="pf-kpi-value">{{ stats.cancelled }}</span>
          <span class="pf-kpi-caption"
            >{{ percent(stats.cancelled, stats.appointments) }} % des rendez-vous</span
          >
        </div>
      </div>
      <div class="pf-split">
        <section class="pf-card pf-chart-card" aria-labelledby="weeks-title">
          <div class="pf-card-head">
            <h2 id="weeks-title">Rendez-vous par semaine</h2>
            <span class="pf-legend"><span class="pf-legend-partial" />semaine incomplète</span>
          </div>
          <div class="pf-chart" role="img" :aria-label="chartLabel">
            <div v-for="w in stats.perWeek" :key="w.start" class="pf-chart-bar">
              <span class="pf-chart-value">{{ w.appointments }}</span>
              <span
                class="pf-chart-fill"
                :class="{ 'pf-partial': w.partial }"
                :style="{ height: `${Math.max(2, (w.appointments / maxWeek) * 100)}%` }"
              />
            </div>
          </div>
          <div class="pf-chart-labels" aria-hidden="true">
            <span v-for="w in stats.perWeek" :key="w.start">{{ weekLabel(w) }}</span>
          </div>
        </section>
        <section class="pf-card pf-table-wrap" aria-labelledby="clinics-title">
          <div class="pf-card-head"><h2 id="clinics-title">Rendez-vous par cabinet</h2></div>
          <table class="pf-table">
            <thead>
              <tr>
                <th scope="col">Cabinet</th>
                <th scope="col" class="pf-num">Praticiens</th>
                <th scope="col" class="pf-num">RDV</th>
                <th scope="col">Part</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="c in stats.perClinic" :key="c.slug">
                <th scope="row">
                  <PfLink :to="`/platform/clinics/${c.slug}`" class="pf-strong-link">{{
                    c.name
                  }}</PfLink>
                </th>
                <td class="pf-num">{{ c.professionals }}</td>
                <td class="pf-num">
                  <strong>{{ c.appointments }}</strong>
                </td>
                <td>
                  <span class="pf-bar-cell">
                    <span class="pf-bar" aria-hidden="true"
                      ><span :style="{ width: `${percent(c.appointments, stats.appointments)}%` }"
                    /></span>
                    <span class="pf-num">{{ percent(c.appointments, stats.appointments) }} %</span>
                  </span>
                </td>
              </tr>
            </tbody>
            <tfoot>
              <tr>
                <th scope="row">Total</th>
                <td class="pf-num">
                  <strong>{{ totalProfessionals }}</strong>
                </td>
                <td class="pf-num">
                  <strong>{{ stats.appointments }}</strong>
                </td>
                <td />
              </tr>
            </tfoot>
          </table>
        </section>
      </div>
      <p class="pf-note">
        <PfIcon name="info" />Uniquement des comptes, calculés côté back ({{ stats.timezone }}).
      </p>
    </template>
    <div v-else class="pf-card pf-empty" aria-busy="true">Chargement des statistiques…</div>
  </div>
</template>
