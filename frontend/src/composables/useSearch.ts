import { ref, type Ref } from 'vue';
import { api } from '../api';
import { nearbyParams, type Position } from '../geo';
import type { Page, Professional, Slot } from '../types';

const SEARCH_CACHE = 'medislot-search';
const PAGE_SIZE = 6;

/** Recherche de praticiens : critères, résultats, créneaux, et dernière liste gardée pour le hors ligne. */
export function useSearch(deps: {
  online: Ref<boolean>;
  error: Ref<string>;
  describe: (e: unknown) => string;
}) {
  const q = ref(''),
    city = ref(''),
    specialty = ref(''),
    page = ref(0),
    total = ref(0),
    loading = ref(false),
    doctors = ref<Professional[]>([]),
    slots = ref<Record<number, Slot[]>>({});
  // Position de « Autour de moi » : en mémoire seulement.
  const near = ref<Position | null>(null);
  // Seule la dernière recherche lancée a le droit d'afficher son résultat.
  let generation = 0;

  async function search(reset = false) {
    if (reset) page.value = 0;
    const current = ++generation;
    loading.value = true;
    deps.error.value = '';
    try {
      const params = near.value
        ? nearbyParams(near.value, specialty.value, page.value, PAGE_SIZE)
        : new URLSearchParams({
            q: q.value,
            city: city.value,
            specialty: specialty.value,
            page: String(page.value),
            size: String(PAGE_SIZE),
          });
      const result = await api<Page<Professional>>('/professionals?' + params);
      if (current !== generation) return;
      doctors.value = result.items;
      total.value = result.total;
      await loadSlots();
      deps.online.value = true;
      // Pas de cache autour d'une position : les distances permettraient de la retrouver.
      try {
        if (!near.value)
          localStorage.setItem(
            SEARCH_CACHE,
            JSON.stringify({ doctors: doctors.value, total: total.value, slots: slots.value }),
          );
      } catch {}
    } catch (e) {
      deps.error.value = deps.describe(e);
      if (!deps.online.value && !doctors.value.length) restoreSearch();
    } finally {
      if (current === generation) loading.value = false;
    }
  }

  async function loadSlots() {
    const entries = await Promise.all(
      doctors.value.map(
        async (p) => [p.id, await api<Slot[]>(`/professionals/${p.id}/slots`)] as const,
      ),
    );
    slots.value = Object.fromEntries(entries);
  }

  // Hors ligne : dernière recherche connue, en lecture seule.
  function restoreSearch() {
    try {
      const cached = JSON.parse(localStorage.getItem(SEARCH_CACHE) || 'null');
      if (cached) {
        doctors.value = cached.doctors;
        total.value = cached.total;
        slots.value = cached.slots;
      }
    } catch {}
  }

  function searchNear(p: Position) {
    near.value = p;
    q.value = '';
    city.value = '';
    search(true);
  }

  function clearNear() {
    near.value = null;
    search(true);
  }

  function resetSearch() {
    q.value = '';
    city.value = '';
    specialty.value = '';
    near.value = null;
    search(true);
  }

  function goToPage(n: number) {
    page.value = n;
    search();
  }

  return {
    q,
    city,
    specialty,
    page,
    total,
    loading,
    doctors,
    slots,
    near,
    search,
    loadSlots,
    searchNear,
    clearNear,
    resetSearch,
    goToPage,
  };
}
