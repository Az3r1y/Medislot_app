<script setup lang="ts">
// Icônes au trait (24 × 24), décoratives : le texte voisin ou aria-label porte le sens.
import { computed } from 'vue';
const props = defineProps<{ name: string; size?: number }>();
const lines = computed<readonly string[]>(() => paths[props.name] ?? []);
</script>
<script lang="ts">
const paths: Record<string, readonly string[]> = {
  dashboard: [
    'M4.5 3h4A1.5 1.5 0 0 1 10 4.5v6A1.5 1.5 0 0 1 8.5 12h-4A1.5 1.5 0 0 1 3 10.5v-6A1.5 1.5 0 0 1 4.5 3z',
    'M15.5 3h4A1.5 1.5 0 0 1 21 4.5v2A1.5 1.5 0 0 1 19.5 8h-4A1.5 1.5 0 0 1 14 6.5v-2A1.5 1.5 0 0 1 15.5 3z',
    'M15.5 12h4a1.5 1.5 0 0 1 1.5 1.5v6a1.5 1.5 0 0 1-1.5 1.5h-4a1.5 1.5 0 0 1-1.5-1.5v-6a1.5 1.5 0 0 1 1.5-1.5z',
    'M4.5 16h4a1.5 1.5 0 0 1 1.5 1.5v2A1.5 1.5 0 0 1 8.5 21h-4A1.5 1.5 0 0 1 3 19.5v-2A1.5 1.5 0 0 1 4.5 16z',
  ],
  building: [
    'M4 21V5a2 2 0 0 1 2-2h8a2 2 0 0 1 2 2v16',
    'M16 9h2a2 2 0 0 1 2 2v10',
    'M3 21h18',
    'M8 7h4M8 11h4M8 15h4',
  ],
  list: ['M8 6h12M8 12h12M8 18h12', 'M4 6h.01M4 12h.01M4 18h.01'],
  shield: ['M12 3l8 3v6c0 4.5-3.4 8.3-8 9-4.6-.7-8-4.5-8-9V6l8-3z'],
  logout: ['M15 4h3a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2h-3', 'M10 17l-5-5 5-5', 'M5 12h11'],
  search: ['M11 4a7 7 0 1 1 0 14 7 7 0 0 1 0-14z', 'M20 20l-3.5-3.5'],
  plus: ['M12 5v14M5 12h14'],
  edit: ['M4 20h4L19 9l-4-4L4 16v4z', 'M14 6l4 4'],
  trash: ['M4 7h16', 'M10 11v6M14 11v6', 'M6 7l1 13h10l1-13', 'M9 7V4h6v3'],
  lock: [
    'M6 11h12a2 2 0 0 1 2 2v6a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2v-6a2 2 0 0 1 2-2z',
    'M8 11V7a4 4 0 0 1 8 0v4',
  ],
  alert: ['M12 3l10 18H2L12 3z', 'M12 10v5', 'M12 18h.01'],
  info: ['M12 3a9 9 0 1 1 0 18 9 9 0 0 1 0-18z', 'M12 11v5M12 8h.01'],
  clock: ['M12 3a9 9 0 1 1 0 18 9 9 0 0 1 0-18z', 'M12 7v5l3 2'],
  calendar: [
    'M5 5h14a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V7a2 2 0 0 1 2-2z',
    'M3 10h18M8 3v4M16 3v4',
  ],
  download: ['M12 4v11', 'M7 10l5 5 5-5', 'M4 20h16'],
  upload: ['M12 16V5', 'M7 10l5-5 5 5', 'M4 20h16'],
  check: ['M5 12l5 5 9-10'],
  close: ['M6 6l12 12M18 6L6 18'],
  ban: ['M12 3a9 9 0 1 1 0 18 9 9 0 0 1 0-18z', 'M5.6 5.6l12.8 12.8'],
  left: ['M15 6l-6 6 6 6'],
  right: ['M9 6l6 6-6 6'],
  down: ['M6 9l6 6 6-6'],
  eye: ['M2 12s3.5-7 10-7 10 7 10 7-3.5 7-10 7S2 12 2 12z', 'M12 9a3 3 0 1 1 0 6 3 3 0 0 1 0-6z'],
  eyeOff: [
    'M3 3l18 18',
    'M10.6 5.1A10 10 0 0 1 12 5c6.5 0 10 7 10 7a17 17 0 0 1-3.2 4.1',
    'M6.6 6.6C3.9 8.4 2 12 2 12s3.5 7 10 7a9.8 9.8 0 0 0 5.4-1.6',
  ],
  user: ['M12 4a4 4 0 1 1 0 8 4 4 0 0 1 0-8z', 'M4 21c1.5-4 4.5-6 8-6s6.5 2 8 6'],
  link: [
    'M10 14a4 4 0 0 0 5.66 0l3-3a4 4 0 0 0-5.66-5.66l-1 1',
    'M14 10a4 4 0 0 0-5.66 0l-3 3a4 4 0 0 0 5.66 5.66l1-1',
  ],
};
</script>
<template>
  <svg
    :width="size ?? 18"
    :height="size ?? 18"
    viewBox="0 0 24 24"
    fill="none"
    stroke="currentColor"
    stroke-width="1.7"
    stroke-linecap="round"
    stroke-linejoin="round"
    aria-hidden="true"
    focusable="false"
  >
    <path v-for="d in lines" :key="d" :d="d" />
  </svg>
</template>
