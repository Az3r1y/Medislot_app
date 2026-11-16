<script setup lang="ts">
import { defineAsyncComponent } from 'vue';
import ProfessionalCard from './ProfessionalCard.vue';
import type { Position } from '../geo';
import type { Professional, Slot } from '../types';
// Leaflet n'est chargé qu'en recherche par position.
const NearbyMap = defineAsyncComponent(() => import('./NearbyMap.vue'));
defineProps<{
  total: number;
  doctors: Professional[];
  slots: Record<number, Slot[]>;
  loading: boolean;
  near: Position | null;
  moveId: number | null;
  page: number;
}>();
const emit = defineEmits<{
  choose: [professional: Professional, slot: Slot];
  profile: [professional: Professional];
  reset: [];
  abandonMove: [];
  goToPage: [page: number];
}>();
</script>

<template>
  <div class="section-title">
    <div>
      <p class="eyebrow dark">VOTRE RECHERCHE</p>
      <h2>{{ total }} praticien{{ total > 1 ? 's' : '' }} à découvrir</h2>
    </div>
    <span class="subtle">Rendez-vous au cabinet</span>
  </div>
  <div v-if="moveId" class="move-banner">
    Déplacement du rendez-vous n° {{ moveId }}
    <button class="text-button" @click="emit('abandonMove')">Abandonner le déplacement</button>
  </div>
  <div role="status">
    <p v-if="loading" class="loading">Nous recherchons vos prochains créneaux…</p>
  </div>
  <template v-if="!loading"
    ><div v-if="!doctors.length" class="empty">
      <h3>Aucun praticien ne correspond à cette recherche</h3>
      <p>
        {{
          near
            ? 'Aucun cabinet à moins de 5 km à vol d’oiseau. Essayez une autre adresse ou une autre spécialité.'
            : 'Essayez une autre ville ou une spécialité différente.'
        }}
      </p>
      <button class="primary" @click="emit('reset')">Voir tous les praticiens</button>
    </div>
    <div v-else class="results">
      <div class="cards">
        <NearbyMap
          v-if="near && doctors.length"
          :center="near"
          :professionals="doctors"
        /><ProfessionalCard
          v-for="p in doctors"
          :key="p.id"
          :professional="p"
          :slots="slots[p.id] || []"
          @choose="(professional, slot) => emit('choose', professional, slot)"
          @profile="emit('profile', $event)"
        />
      </div>
      <aside class="help">
        <span class="help-icon" aria-hidden="true">+</span>
        <h2>Votre rendez-vous,<br />en toute simplicité.</h2>
        <ol>
          <li>Choisissez votre praticien.</li>
          <li>Sélectionnez un créneau.</li>
          <li>Retrouvez la confirmation dans votre espace.</li>
        </ol>
        <hr />
        <p>
          Un changement de programme ? Vous pouvez déplacer ou annuler votre rendez-vous depuis
          votre espace.
        </p>
        <p class="demo-note">Démonstration pédagogique.<br />Praticiens et données fictifs.</p>
      </aside>
    </div></template
  >
  <div v-if="total > 6" class="pagination">
    <button :disabled="page === 0" @click="emit('goToPage', page - 1)">Précédent</button
    ><span>Page {{ page + 1 }}</span
    ><button :disabled="(page + 1) * 6 >= total" @click="emit('goToPage', page + 1)">
      Suivant
    </button>
  </div>
</template>
