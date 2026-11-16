<script setup lang="ts">
import { dateLabel, timeLabel, type Appointment } from '../types';
defineProps<{ appointments: Appointment[]; loading: boolean }>();
</script>
<template>
  <section aria-labelledby="agenda-title" :aria-busy="loading">
    <h1 id="agenda-title">Agenda du cabinet</h1>
    <p v-if="loading">Chargement de l’agenda…</p>
    <p v-else-if="!appointments.length">Aucun rendez-vous dans ce cabinet.</p>
    <ul v-else class="clinic-agenda">
      <li v-for="item in appointments" :key="item.id">
        <strong>{{ item.professionalName }}</strong>
        <span>{{ dateLabel(item.startsAt) }} à {{ timeLabel(item.startsAt) }}</span>
        <span>{{ item.status === 'CONFIRMED' ? 'Confirmé' : 'Annulé' }}</span>
      </li>
    </ul>
  </section>
</template>
