<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { platformApi } from '../platformApi';
import { messageOf, useConsole } from '../context';
import { dateLabel, inkOn, slotLabel } from '../format';
import { specialties } from '../../types';
import PfIcon from '../components/PfIcon.vue';
import PfLink from '../components/PfLink.vue';
import ClinicTabs from '../components/ClinicTabs.vue';
import type { ClinicDetail } from '../types';

// Point d'entrée vers les praticiens, le journal et les statistiques d'un seul cabinet.
const props = defineProps<{ slug: string }>();
const { fail } = useConsole();
const detail = ref<ClinicDetail | null>(null),
  error = ref('');

async function load() {
  error.value = '';
  try {
    detail.value = await platformApi<ClinicDetail>(`/clinics/${props.slug}`);
  } catch (e) {
    if (!fail(e)) error.value = messageOf(e);
  }
}
onMounted(load);
const initials = (name: string) =>
  name
    .replace(/^Dr\.?\s+/i, '')
    .split(/\s+/)
    .map((w) => w.charAt(0))
    .join('')
    .slice(0, 2)
    .toUpperCase();
const tones = ['#e6f2f7', '#eaf7f1', '#fdf3e7', '#e5f2fa'];
</script>
<template>
  <div class="pf-page">
    <nav class="pf-crumb" aria-label="Fil d’Ariane">
      <PfLink to="/platform/clinics">Cabinets</PfLink>
      <PfIcon name="right" :size="14" />
      <span aria-current="page">{{ detail?.clinic.name ?? slug }}</span>
    </nav>
    <p v-if="error" class="pf-alert pf-alert-error" role="alert">
      <PfIcon name="alert" /><span>{{ error }}</span>
      <button type="button" class="pf-link-button" @click="load">Réessayer</button>
    </p>
    <div v-else-if="!detail" class="pf-card pf-empty" aria-busy="true">Chargement du cabinet…</div>
    <template v-else>
      <header class="pf-card pf-clinic-head">
        <img
          v-if="detail.clinic.hasLogo"
          class="pf-tile pf-tile-lg"
          :src="`/api/platform/clinics/${slug}/logo`"
          alt=""
        />
        <span
          v-else
          class="pf-tile pf-tile-lg"
          :style="{ background: detail.clinic.color, color: inkOn(detail.clinic.color) }"
          aria-hidden="true"
          >{{ detail.clinic.name.charAt(0) }}</span
        >
        <div class="pf-grow">
          <h1 class="pf-title">{{ detail.clinic.name }}</h1>
          <ul class="pf-chips">
            <li class="pf-mono">{{ detail.clinic.slug }}</li>
            <li>{{ detail.clinic.city }}</li>
            <li v-if="detail.clinic.address">{{ detail.clinic.address }}</li>
            <li>
              <strong>{{ detail.clinic.professionals }}</strong> praticiens
            </li>
            <li>
              <strong>{{ detail.clinic.appointments30d }}</strong> RDV (30 j)
            </li>
            <li>Créé le {{ dateLabel(detail.clinic.createdAt) }}</li>
          </ul>
        </div>
        <PfLink :to="`/platform/clinics/${slug}/edit`" class="pf-button"
          ><PfIcon name="edit" />Modifier</PfLink
        >
      </header>
      <ClinicTabs :slug="slug" current="practitioners" :count="detail.practitioners.length" />
      <div v-if="!detail.practitioners.length" class="pf-card pf-empty">
        Aucun praticien dans ce cabinet pour le moment.
      </div>
      <div v-else class="pf-card pf-table-wrap">
        <table class="pf-table">
          <thead>
            <tr>
              <th scope="col">Praticien</th>
              <th scope="col">Spécialité</th>
              <th scope="col" class="pf-num">RDV (30 j)</th>
              <th scope="col">Prochain créneau libre</th>
              <th scope="col"><span class="pf-visually-hidden">Actions</span></th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(p, index) in detail.practitioners" :key="p.id">
              <th scope="row">
                <span class="pf-identity">
                  <span
                    class="pf-avatar"
                    :style="{ background: tones[index % tones.length] }"
                    aria-hidden="true"
                    >{{ initials(p.name) }}</span
                  >
                  <span class="pf-strong">{{ p.name }}</span>
                </span>
              </th>
              <td>{{ specialties[p.specialty] ?? p.specialty }}</td>
              <td class="pf-num">
                <strong>{{ p.appointments30d }}</strong>
              </td>
              <td>
                <span v-if="p.nextFreeSlot" class="pf-slot"
                  ><PfIcon name="calendar" :size="15" />{{ slotLabel(p.nextFreeSlot) }}</span
                >
                <span v-else class="pf-muted">Aucun créneau libre</span>
              </td>
              <td class="pf-end">
                <PfLink
                  :to="`/platform/clinics/${slug}/practitioners/${p.id}`"
                  class="pf-button pf-small-button"
                  :aria-label="`Voir les rendez-vous de ${p.name}`"
                  >Voir les rendez-vous</PfLink
                >
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </template>
  </div>
</template>
