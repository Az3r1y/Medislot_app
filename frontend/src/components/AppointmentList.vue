<script setup lang="ts">
import ConvocationButton from './ConvocationButton.vue';
import CalendarActions from './CalendarActions.vue';
import CalendarSubscription from './CalendarSubscription.vue';
import { specialties, dateLabel, timeLabel, type Appointment } from '../types';
defineProps<{ appointments: Appointment[] }>();
const emit = defineEmits<{
  book: [];
  move: [Appointment];
  cancel: [id: number];
  history: [id: number];
}>();
// libellé complet pour les lecteurs d'écran
const rdv = (a: Appointment) =>
  `${a.professionalName}, ${dateLabel(a.startsAt)} à ${timeLabel(a.startsAt)}`;
</script>

<template>
  <div class="section-title">
    <div>
      <p class="eyebrow dark">VOTRE ESPACE</p>
      <h1 tabindex="-1">Mes rendez-vous</h1>
    </div>
    <button class="primary" @click="emit('book')">Prendre rendez-vous</button>
  </div>
  <p v-if="!appointments.length" class="empty">Vous n’avez pas encore de rendez-vous.</p>
  <article v-for="a in appointments" :key="a.id" class="appointment">
    <div class="appointment-date">
      <strong>{{ dateLabel(a.startsAt) }}</strong><span>{{ timeLabel(a.startsAt) }}</span>
    </div>
    <div class="appointment-person">
      <h2>{{ a.professionalName }}</h2>
      <p>{{ specialties[a.specialty] }} · {{ a.city }}</p>
      <span class="status" :class="{ cancelled: a.status === 'CANCELLED' }">{{
        a.status === 'CONFIRMED' ? 'Confirmé' : 'Annulé'
      }}</span>
    </div>
    <div class="appointment-actions">
      <button
        v-if="a.status === 'CONFIRMED'"
        :aria-label="'Déplacer le rendez-vous : ' + rdv(a)"
        @click="emit('move', a)"
      >
        Déplacer</button
      ><button
        v-if="a.status === 'CONFIRMED'"
        class="danger-link"
        :aria-label="'Annuler le rendez-vous : ' + rdv(a)"
        @click="emit('cancel', a.id)"
      >
        Annuler</button
      ><button
        class="text-button"
        :aria-label="'Historique du rendez-vous : ' + rdv(a)"
        @click="emit('history', a.id)"
      >
        Historique</button
      ><ConvocationButton v-if="a.status === 'CONFIRMED'" :id="a.id" :rdv="rdv(a)" />
      <p v-else class="convocation-off">Plus de convocation : rendez-vous annulé.</p>
      <CalendarActions :appointment="a" />
    </div>
  </article>
  <CalendarSubscription />
</template>
