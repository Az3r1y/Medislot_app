import { defineStore } from 'pinia';
import { computed, ref } from 'vue';
import { api } from '../api';
import { clinicFromHost, clinicFromPath, clinicPath, safeColor, validSlug } from '../tenant';
import { useAgenda } from './agenda';

export interface Clinic {
  id: number;
  slug: string;
  name: string;
  city: string;
  color: string;
}
export const useClinic = defineStore('clinic', () => {
  const slug = ref(
    clinicFromPath(globalThis.window?.location.pathname ?? '') ??
      clinicFromHost(globalThis.window?.location.host ?? '') ??
      'cabinet-a',
  );
  const revision = ref(0);
  const clinics = ref<Clinic[]>([]);
  const mine = ref<Clinic[]>([]);
  const authenticated = ref(false);
  const available = computed(() => (authenticated.value ? mine.value : clinics.value));
  const current = computed(() => available.value.find((c) => c.slug === slug.value));
  const color = computed(() => safeColor(current.value?.color));

  function switchTo(next: string, push = true): boolean {
    if (!validSlug(next) || !available.value.some((c) => c.slug === next))
      throw new Error('Ce cabinet ne fait pas partie de vos accès.');
    if (next === slug.value) return false;
    useAgenda().clear();
    revision.value++;
    slug.value = next;
    if (push) globalThis.window?.history.pushState(null, '', clinicPath(next));
    return true;
  }
  async function load() {
    clinics.value = await api<Clinic[]>('/clinics');
    globalThis.window?.history.replaceState(null, '', clinicPath(slug.value));
  }
  async function afterLogin(): Promise<boolean> {
    mine.value = await api<Clinic[]>('/clinics');
    authenticated.value = true;
    useAgenda().clear();
    revision.value++;
    if (mine.value.some((c) => c.slug === slug.value)) return false;
    const first = mine.value[0];
    if (!first) throw new Error('Aucun cabinet accessible pour ce compte.');
    return switchTo(first.slug);
  }
  function afterLogout() {
    useAgenda().clear();
    revision.value++;
    mine.value = [];
    authenticated.value = false;
  }
  return {
    slug,
    revision,
    clinics,
    mine,
    authenticated,
    available,
    current,
    color,
    switchTo,
    load,
    afterLogin,
    afterLogout,
  };
});
