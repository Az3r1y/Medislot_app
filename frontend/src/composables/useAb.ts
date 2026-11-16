import { ref } from 'vue';
import { api } from '../api';
export function useAb() {
  const ready = ref(false),
    enabled = ref(false),
    variant = ref<'A' | 'B'>('A'),
    measure = ref(false);
  // secours : version A, aucune attribution demandée (measure=false)
  async function load() {
    try {
      const a = await api<{ enabled: boolean; variant: 'A' | 'B' }>('/ab/assignment');
      enabled.value = a.enabled;
      variant.value = a.variant;
      measure.value = a.enabled;
    } catch {
      enabled.value = false;
      variant.value = 'A';
      measure.value = false;
    } finally {
      ready.value = true;
    }
  }
  // une exposition perdue ne doit pas bloquer la réservation ; le serveur ne compte que la première
  async function expose() {
    try {
      await api('/ab/exposure', { method: 'POST' });
    } catch {}
  }
  return { ready, enabled, variant, measure, load, expose };
}
