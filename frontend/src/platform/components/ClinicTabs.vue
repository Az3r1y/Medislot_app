<script setup lang="ts">
import PfLink from './PfLink.vue';
defineProps<{ slug: string; current: 'practitioners' | 'journal'; count?: number }>();
</script>
<template>
  <nav class="pf-tabs" aria-label="Sections du cabinet">
    <PfLink
      :to="`/platform/clinics/${slug}`"
      class="pf-tab"
      :class="{ 'pf-tab-on': current === 'practitioners' }"
      :aria-current="current === 'practitioners' ? 'page' : undefined"
      >Praticiens<span v-if="count !== undefined" class="pf-tab-count">{{ count }}</span></PfLink
    >
    <PfLink
      :to="`/platform/clinics/${slug}/journal`"
      class="pf-tab"
      :class="{ 'pf-tab-on': current === 'journal' }"
      :aria-current="current === 'journal' ? 'page' : undefined"
      >Journal</PfLink
    >
    <PfLink :to="`/platform/dashboard?clinic=${slug}`" class="pf-tab">Statistiques</PfLink>
  </nav>
</template>
