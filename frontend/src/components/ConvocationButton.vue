<script setup lang="ts">
import { ref } from 'vue';
import { openConvocation, saveConvocation } from '../convocation';
// rdv : description du rendez-vous pour les lecteurs d'écran
const props = defineProps<{ id: number; rdv: string }>();
const state = ref<'idle' | 'loading' | 'error'>('idle');
const message = ref('');
async function run(action: typeof openConvocation) {
  if (state.value === 'loading') return;
  state.value = 'loading';
  message.value = '';
  try {
    await action(props.id);
    state.value = 'idle';
  } catch (e) {
    state.value = 'error';
    message.value = e instanceof Error ? e.message : 'Une erreur est survenue.';
  }
}
</script>

<template>
  <div class="convocation" :aria-busy="state === 'loading'">
    <!-- openConvocation ouvre l'onglet avant tout await -->
    <button
      class="text-button"
      :disabled="state === 'loading'"
      :aria-label="'Ma convocation (PDF) : ' + rdv"
      @click="run(openConvocation)"
    >
      {{ state === 'loading' ? 'Préparation…' : 'Ma convocation (PDF)' }}
    </button>
    <button
      class="text-button"
      :disabled="state === 'loading'"
      :aria-label="'Enregistrer la convocation : ' + rdv"
      @click="run(saveConvocation)"
    >
      Enregistrer
    </button>
    <p class="convocation-message" role="status">{{ state === 'error' ? message : '' }}</p>
  </div>
</template>

<style scoped>
.convocation {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  align-items: baseline;
}
.convocation button[disabled] {
  opacity: 0.6;
  cursor: progress;
}
.convocation-message {
  flex-basis: 100%;
  margin: 0;
  font-size: 13px;
  color: #9e4433;
}
.convocation-message:empty {
  display: none;
}
</style>
