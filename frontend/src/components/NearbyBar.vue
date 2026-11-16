<script setup lang="ts">
import { ref } from 'vue';
import { geocode, getPosition, type Position } from '../geo';
defineProps<{ active: Position | null }>();
const emit = defineEmits<{ position: [Position]; clear: [] }>();
const address = ref('');
const busy = ref(false);
const message = ref('');
// La position n'est demandée qu'au clic.
async function locate(source: () => Promise<Position>) {
  if (busy.value) return;
  busy.value = true;
  message.value = '';
  try {
    emit('position', await source());
  } catch (e) {
    message.value = e instanceof Error ? e.message : 'Une erreur est survenue.';
  } finally {
    busy.value = false;
  }
}
</script>

<template>
  <div class="nearby">
    <button
      type="button"
      class="nearby-me"
      :aria-disabled="busy"
      @click="locate(() => getPosition())"
    >
      <span aria-hidden="true">◎</span> {{ busy ? 'Localisation…' : 'Autour de moi (5 km)' }}
    </button>
    <form class="nearby-address" @submit.prevent="locate(() => geocode(address))">
      <label
        ><span class="sr-only">Chercher autour d'une adresse ou d'un code postal</span>
        <input
          v-model="address"
          placeholder="ou une adresse, un code postal"
          autocomplete="off"
          maxlength="200"
      /></label>
      <button type="submit" :aria-disabled="busy">Chercher autour</button>
    </form>
    <p v-if="active" class="nearby-active">
      Autour de : <strong>{{ active.label }}</strong> · 5 km à vol d’oiseau
      <button type="button" class="text-button" @click="emit('clear')">Effacer</button>
    </p>
    <p class="nearby-message" role="status">{{ message }}</p>
  </div>
</template>

<style scoped>
.nearby {
  display: flex;
  flex-wrap: wrap;
  gap: 10px 16px;
  align-items: center;
  margin-top: 14px;
}
.nearby-me,
.nearby-address button {
  padding: 8px 14px;
  border-radius: 20px;
  font-weight: 600;
}
.nearby-address {
  display: flex;
  gap: 8px;
  flex: 1 1 280px;
}
.nearby-address label {
  flex: 1;
}
.nearby-address input {
  width: 100%;
}
.nearby-active,
.nearby-message {
  flex-basis: 100%;
  margin: 0;
  font-size: 14px;
}
.nearby-message {
  color: #9e4433;
}
.nearby-message:empty {
  display: none;
}
</style>
