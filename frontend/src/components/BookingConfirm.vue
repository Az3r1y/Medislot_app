<script setup lang="ts">
import { specialties, dateLabel, timeLabel, type Professional, type Slot } from '../types';
defineProps<{
  professional: Professional;
  slot: Slot;
  error: string;
  label: string;
  ready: boolean;
  disabled: boolean;
  busy: boolean;
}>();
const emit = defineEmits<{ confirm: [] }>();
</script>

<template>
  <p class="eyebrow dark">DERNIÈRE ÉTAPE</p>
  <h2 id="modal-title" tabindex="-1">Confirmer votre rendez-vous</h2>
  <div class="booking-summary">
    <h3>{{ professional.name }}</h3>
    <p>{{ specialties[professional.specialty] }}</p>
    <strong>{{ dateLabel(slot.startsAt) }} à {{ timeLabel(slot.startsAt) }}</strong>
    <p>{{ professional.address }}, {{ professional.city }}</p>
  </div>
  <p class="subtle">
    Le créneau sera réservé après confirmation du serveur. Une autre personne peut encore le
    sélectionner entre-temps.
  </p>
  <div role="alert">
    <p v-if="error" class="error-text">{{ error }}</p>
  </div>
  <!-- data-ab-confirm : sert à mesurer l'exposition du test A/B -->
  <button
    v-if="ready"
    class="primary wide"
    data-ab-confirm
    :aria-disabled="disabled"
    @click="emit('confirm')"
  >
    {{ busy ? 'Confirmation…' : label }}
  </button>
</template>
