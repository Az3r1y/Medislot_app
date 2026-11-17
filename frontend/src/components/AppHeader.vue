<script setup lang="ts">
import { onUnmounted, ref } from 'vue';
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

const audioError = ref('');
let logoClicks = 0;
let audio: HTMLAudioElement | undefined;
let audioContext: AudioContext | undefined;
let animationFrame = 0;

function logoClick() {
  emit('home');
  if (audio) return;
  logoClicks++;
  if (logoClicks !== 4) return;
  logoClicks = 0;
  audioError.value = '';

  try {
    const player = new Audio('/audio/harlem-shake.mp3');
    player.loop = true;
    audio = player;
    const context = new AudioContext();
    audioContext = context;
    const source = context.createMediaElementSource(player);
    const bass = context.createBiquadFilter();
    bass.type = 'lowshelf';
    bass.frequency.value = 150;
    bass.gain.value = 8;
    const analyser = context.createAnalyser();
    analyser.fftSize = 256;
    const limiter = context.createDynamicsCompressor();
    limiter.threshold.value = -8;
    limiter.ratio.value = 12;
    source.connect(bass).connect(analyser).connect(limiter).connect(context.destination);
    const frequencies = new Uint8Array(analyser.frequencyBinCount);
    let phase = '';

    function animate() {
      if (!audio) return;
      const next = player.currentTime >= 15.6 ? 'harlem-drop' : 'harlem-intro';
      if (next !== phase) {
        document.body.classList.remove('harlem-intro', 'harlem-drop');
        document.body.classList.add(next);
        phase = next;
      }
      analyser.getByteFrequencyData(frequencies);
      const beat = (frequencies[0] + frequencies[1]) / 510;
      const buildup = Math.min(player.currentTime / 15.6, 1);
      document.body.style.setProperty('--harlem-beat', beat.toFixed(3));
      document.body.style.setProperty('--harlem-build', buildup.toFixed(3));
      animationFrame = requestAnimationFrame(animate);
    }

    const playing = player.play();
    void context.resume().catch(() => {});
    void playing.then(animate).catch(() => {
      cleanup();
      audioError.value =
        'Impossible de lancer la musique. Cliquez à nouveau quatre fois sur le logo.';
    });
  } catch {
    cleanup();
    audioError.value =
      'Impossible de lancer la musique. Cliquez à nouveau quatre fois sur le logo.';
  }
}

function cleanup() {
  cancelAnimationFrame(animationFrame);
  audio?.pause();
  audio = undefined;
  if (audioContext) void audioContext.close().catch(() => {});
  audioContext = undefined;
  document.body.classList.remove('harlem-intro', 'harlem-drop');
  document.body.style.removeProperty('--harlem-beat');
  document.body.style.removeProperty('--harlem-build');
}

onUnmounted(cleanup);
</script>

<template>
  <header class="header">
    <button class="brand" @click="logoClick">MediSlot<span class="brand-dot">.</span></button>
    <span class="sr-only" role="alert">{{ audioError }}</span>
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
