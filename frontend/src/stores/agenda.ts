import { defineStore } from 'pinia';
import { ref } from 'vue';
import { api } from '../api';
import type { Appointment } from '../types';

export const useAgenda = defineStore('agenda', () => {
  const appointments = ref<Appointment[]>([]);
  const loading = ref(false);
  let generation = 0;
  function clear() {
    generation++;
    appointments.value = [];
    loading.value = false;
  }
  async function load(secretary = false) {
    const requested = ++generation;
    loading.value = true;
    try {
      const result = await api<Appointment[]>(secretary ? '/clinic/agenda' : '/appointments');
      if (requested === generation) appointments.value = result;
    } catch (error) {
      if (requested === generation) throw error;
    } finally {
      if (requested === generation) loading.value = false;
    }
  }
  return { appointments, loading, clear, load };
});
