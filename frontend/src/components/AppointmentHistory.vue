<script setup lang="ts">
import { dateLabel, timeLabel } from '../types';
defineProps<{ history: { action: string; createdAt: string }[] }>();
const labels: Record<string, string> = {
  CREATED: 'Réservation confirmée',
  RESCHEDULED: 'Rendez-vous déplacé',
  CANCELLED: 'Rendez-vous annulé',
};
</script>

<template>
  <h2 id="modal-title" tabindex="-1">Historique du rendez-vous</h2>
  <ol class="timeline">
    <li v-for="(h, i) in history" :key="i">
      <strong>{{ labels[h.action] ?? h.action }}</strong>
      <p>{{ dateLabel(h.createdAt) }} à {{ timeLabel(h.createdAt) }}</p>
    </li>
  </ol>
</template>
