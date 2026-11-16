import { demarrerTraces } from './tracing';
import { createApp } from 'vue';
import { createPinia } from 'pinia';
import App from './App.vue';
// Polices auto-hébergées : rien n'est demandé à Google Fonts.
import '@fontsource/dm-sans/400.css';
import '@fontsource/dm-sans/500.css';
import '@fontsource/dm-sans/600.css';
import '@fontsource/dm-sans/700.css';
import '@fontsource/manrope/400.css';
import '@fontsource/manrope/500.css';
import '@fontsource/manrope/600.css';
import '@fontsource/manrope/700.css';
import '@fontsource/manrope/800.css';
import './style.css';
// Avant le montage : la première requête doit déjà porter traceparent.
demarrerTraces();
createApp(App).use(createPinia()).mount('#app');
