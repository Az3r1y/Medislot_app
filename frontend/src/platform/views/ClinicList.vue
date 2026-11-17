<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { platformApi } from '../platformApi';
import { messageOf, useConsole } from '../context';
import { dateLabel, inkOn } from '../format';
import PfIcon from '../components/PfIcon.vue';
import PfLink from '../components/PfLink.vue';
import DeleteClinicDialog from '../components/DeleteClinicDialog.vue';
import type { ClinicRow } from '../types';

const { fail, notify } = useConsole();
const clinics = ref<ClinicRow[] | null>(null),
  q = ref(''),
  city = ref(''),
  error = ref(''),
  removing = ref<ClinicRow | null>(null);
let all: ClinicRow[] = [];

// Données : GET /api/platform/clinics, servies par une vue agrégée (comptes seulement).
async function load() {
  error.value = '';
  try {
    all = await platformApi<ClinicRow[]>('/clinics');
    clinics.value = all;
  } catch (e) {
    if (!fail(e)) error.value = messageOf(e);
  }
}
onMounted(load);

const cities = computed(() =>
  [...new Set(all.map((c) => c.city))].sort((a, b) => a.localeCompare(b, 'fr')),
);
const shown = computed(() => {
  const term = q.value.trim().toLowerCase();
  return (clinics.value ?? []).filter(
    (c) =>
      (!term || c.name.toLowerCase().includes(term) || c.slug.includes(term)) &&
      (!city.value || c.city === city.value),
  );
});
const max = computed(() => Math.max(1, ...shown.value.map((c) => c.appointments30d)));

function removed(clinic: ClinicRow) {
  removing.value = null;
  notify(`Le cabinet « ${clinic.name} » a été supprimé.`);
  load();
}
</script>
<template>
  <div class="pf-page">
    <header class="pf-page-head">
      <div>
        <h1 class="pf-title">
          Cabinets <span v-if="clinics" class="pf-count">{{ clinics.length }}</span>
        </h1>
        <p class="pf-subtitle">Chiffres agrégés uniquement · aucune donnée patient</p>
      </div>
      <PfLink to="/platform/clinics/new" class="pf-button pf-primary">
        <PfIcon name="plus" />Créer un cabinet
      </PfLink>
    </header>
    <div class="pf-filters" role="search">
      <label class="pf-search">
        <PfIcon name="search" />
        <span class="pf-visually-hidden">Rechercher un cabinet</span>
        <input
          v-model="q"
          type="search"
          placeholder="Rechercher un cabinet (nom ou identifiant)…"
        />
      </label>
      <label class="pf-select">
        <span>Ville</span>
        <select v-model="city">
          <option value="">toutes</option>
          <option v-for="c in cities" :key="c" :value="c">{{ c }}</option>
        </select>
      </label>
    </div>
    <p v-if="error" class="pf-alert pf-alert-error" role="alert">
      <PfIcon name="alert" /><span>{{ error }}</span>
      <button type="button" class="pf-link-button" @click="load">Réessayer</button>
    </p>
    <div v-else-if="!clinics" class="pf-card pf-empty" aria-busy="true">
      Chargement des cabinets…
    </div>
    <div v-else-if="!shown.length" class="pf-card pf-empty">
      Aucun cabinet ne correspond à ces critères.
    </div>
    <div v-else class="pf-card pf-table-wrap">
      <table class="pf-table">
        <thead>
          <tr>
            <th scope="col">Cabinet</th>
            <th scope="col">Ville</th>
            <th scope="col" class="pf-num">Praticiens</th>
            <th scope="col">RDV (30 j)</th>
            <th scope="col">Créé le</th>
            <th scope="col"><span class="pf-visually-hidden">Actions</span></th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="c in shown" :key="c.id">
            <td>
              <span class="pf-identity">
                <img
                  v-if="c.hasLogo"
                  class="pf-tile"
                  :src="`/api/platform/clinics/${c.slug}/logo`"
                  alt=""
                />
                <span
                  v-else
                  class="pf-tile"
                  :style="{ background: c.color, color: inkOn(c.color) }"
                  aria-hidden="true"
                  >{{ c.name.charAt(0) }}</span
                >
                <span>
                  <PfLink :to="`/platform/clinics/${c.slug}`" class="pf-strong-link">{{
                    c.name
                  }}</PfLink>
                  <span class="pf-mono pf-muted">{{ c.slug }}</span>
                </span>
              </span>
            </td>
            <td>{{ c.city }}</td>
            <td class="pf-num">{{ c.professionals }}</td>
            <td>
              <span class="pf-bar-cell">
                <strong class="pf-num">{{ c.appointments30d }}</strong>
                <span class="pf-bar" aria-hidden="true"
                  ><span :style="{ width: `${(c.appointments30d / max) * 100}%` }"
                /></span>
              </span>
            </td>
            <td class="pf-num">{{ dateLabel(c.createdAt) }}</td>
            <td>
              <span class="pf-actions">
                <PfLink :to="`/platform/clinics/${c.slug}`" class="pf-button pf-small-button"
                  >Ouvrir</PfLink
                >
                <PfLink
                  :to="`/platform/clinics/${c.slug}/edit`"
                  class="pf-icon-button pf-bordered"
                  :aria-label="`Modifier ${c.name}`"
                  ><PfIcon name="edit"
                /></PfLink>
                <button
                  type="button"
                  class="pf-icon-button pf-bordered pf-danger-icon"
                  :aria-label="`Supprimer ${c.name}`"
                  @click="removing = c"
                >
                  <PfIcon name="trash" />
                </button>
              </span>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
    <p class="pf-note">
      <PfIcon name="info" />Le master ne voit que des comptes (praticiens, rendez-vous), jamais un
      patient.
    </p>
    <DeleteClinicDialog
      v-if="removing"
      :clinic="removing"
      @close="removing = null"
      @deleted="removed"
    />
  </div>
</template>
