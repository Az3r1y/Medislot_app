<script setup lang="ts">
import type { Identity } from '../types';
defineProps<{
  view: 'search' | 'appointments';
  user: Identity | null;
  noticeCount: number;
  showNotices: boolean;
}>();
const emit = defineEmits<{
  home: [];
  search: [];
  appointments: [];
  toggleNotices: [];
  login: [];
  logout: [];
}>();
</script>

<template>
  <header class="header">
    <button class="brand" @click="emit('home')">MediSlot<span class="brand-dot">.</span></button>
    <nav aria-label="Navigation principale">
      <button :aria-current="view === 'search' ? 'page' : undefined" @click="emit('search')">
        Trouver un praticien</button
      ><button
        :aria-current="view === 'appointments' ? 'page' : undefined"
        @click="emit('appointments')"
      >
        {{
          user?.role === 'SECRETARY' || user?.role === 'ADMIN'
            ? 'Agenda du cabinet'
            : 'Mes rendez-vous'
        }}</button
      ><button v-if="user" @click="emit('toggleNotices')" :aria-expanded="showNotices">
        Notifications <span class="count">{{ noticeCount }}</span></button
      ><button v-if="!user" class="login" @click="emit('login')">Se connecter</button
      ><button v-else class="login" @click="emit('logout')">
        {{ user.name.split(' ')[0] }} · Déconnexion
      </button>
    </nav>
  </header>
</template>
