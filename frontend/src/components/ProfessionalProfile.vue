<script setup lang="ts">
import { specialties, type Professional } from '../types';
defineProps<{ profile: Professional }>();
const emit = defineEmits<{ close: [] }>();
</script>

<template>
  <p class="eyebrow dark">VOTRE PRATICIEN</p>
  <h2 id="modal-title" tabindex="-1">{{ profile.name }}</h2>
  <p class="specialty">{{ specialties[profile.specialty] }}</p>
  <p>{{ profile.bio }}</p>
  <h3>Le cabinet</h3>
  <p>{{ profile.address }}, {{ profile.city }}</p>
  <h3>Consultation</h3>
  <p>{{ profile.fee }} € · 30 minutes · Au cabinet</p>
  <button class="primary wide" @click="emit('close')">Voir les disponibilités</button>
</template>
