<script setup lang="ts">
import { ref } from 'vue';
import { fetchCalendar } from '../calendar';
import type { Appointment } from '../types';
const props = defineProps<{ appointment: Appointment }>();
const error = ref('');
const success = ref('');
const busy = ref(false);

function googleUrl() {
  const a = props.appointment;
  const format = (date: string) =>
    new Date(date)
      .toISOString()
      .replace(/[-:]/g, '')
      .replace(/\.\d{3}/, '');
  const params = new URLSearchParams({
    action: 'TEMPLATE',
    text: `Rendez-vous avec ${a.professionalName}`,
    dates: `${format(a.startsAt)}/${format(a.endsAt)}`,
    details: `Rendez-vous avec ${a.professionalName} (${a.specialty})`,
    location: `${a.address}, ${a.city}`,
  });
  return `https://calendar.google.com/calendar/render?${params}`;
}

async function download() {
  busy.value = true;
  error.value = '';
  success.value = '';
  try {
    const blob = await fetchCalendar(props.appointment.id);
    const url = URL.createObjectURL(blob);
    const anchor = document.createElement('a');
    anchor.href = url;
    anchor.download = 'rendez-vous.ics';
    anchor.click();
    success.value = 'Le fichier calendrier a été téléchargé.';
    setTimeout(() => URL.revokeObjectURL(url), 60_000);
  } catch {
    error.value =
      'Impossible de télécharger le rendez-vous. Vérifiez votre connexion et votre session.';
  } finally {
    busy.value = false;
  }
}
</script>

<template>
  <button :disabled="busy" @click="download">Ajouter à mon agenda</button>
  <a
    v-if="appointment.status === 'CONFIRMED'"
    :href="googleUrl()"
    target="_blank"
    rel="noopener noreferrer"
    >Ajouter à Google Agenda</a
  >
  <p role="alert">{{ error }}</p>
  <p role="status">{{ success }}</p>
</template>
