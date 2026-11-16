<script setup lang="ts">
import NearbyBar from './NearbyBar.vue';
import { specialties } from '../types';
import type { Position } from '../geo';
const q = defineModel<string>('q', { required: true });
const specialty = defineModel<string>('specialty', { required: true });
const city = defineModel<string>('city', { required: true });
defineProps<{ loading: boolean; near: Position | null; liveConnected: boolean }>();
const emit = defineEmits<{ submit: []; position: [Position]; clearNear: [] }>();
</script>

<template>
  <section class="hero">
    <div class="hero-inner">
      <p class="eyebrow">VOTRE PROCHAIN RENDEZ-VOUS</p>
      <h1 tabindex="-1">Trouvez le bon praticien.<br />Prenez un moment pour vous.</h1>
      <p class="hero-copy">Un professionnel près de chez vous, au créneau qui vous convient.</p>
      <!-- une nouvelle recherche remplace celle en cours -->
      <form class="searchbar" @submit.prevent="emit('submit')">
        <label
          ><span>Nom du praticien</span
          ><input v-model="q" placeholder="Nom, praticien…" autocomplete="off" /></label
        ><label
          ><span>Spécialité</span
          ><select v-model="specialty">
            <option value="">Toutes les spécialités</option>
            <option v-for="(label, key) in specialties" :key="key" :value="key">
              {{ label }}
            </option>
          </select></label
        ><label
          ><span>Ville</span
          ><input v-model="city" placeholder="Paris, Lyon…" autocomplete="address-level2" /></label
        ><button type="submit">
          {{ loading ? 'Recherche…' : 'Rechercher' }} <span aria-hidden="true">→</span>
        </button>
      </form>
      <NearbyBar :active="near" @position="emit('position', $event)" @clear="emit('clearNear')" />
      <div class="hero-bottom">
        <span>Des créneaux visibles en quelques clics</span
        ><span class="live" :class="{ offline: !liveConnected }"
          ><i></i>{{ liveConnected ? 'Disponibilités en direct' : 'Reconnexion du direct…' }}</span
        >
      </div>
    </div>
  </section>
</template>
