<script setup lang="ts">
defineProps<{ disabled: boolean }>();
const emit = defineEmits<{ confirm: []; keep: [] }>();
</script>

<template>
  <h2 id="modal-title" tabindex="-1">Annuler ce rendez-vous ?</h2>
  <p>
    Le créneau sera remis à disposition. Vous pourrez prendre un autre rendez-vous quand vous le
    souhaitez.
  </p>
  <button class="primary wide" :aria-disabled="disabled" @click="emit('confirm')">
    Confirmer l’annulation</button
  ><button class="text-button" @click="emit('keep')">Conserver mon rendez-vous</button>
</template>
