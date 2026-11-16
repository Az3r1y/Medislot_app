import { defineConfig } from 'vite';
import vue from '@vitejs/plugin-vue';
export default defineConfig({
  plugins: [vue()],
  // Polices en fichiers, jamais en data: (CSP font-src 'self').
  build: { assetsInlineLimit: (file) => (/\.(woff2?|ttf)$/.test(file) ? false : undefined) },
  server: {
    port: 5173,
    proxy: {
      '/api': { target: process.env.API_TARGET || 'http://127.0.0.1:18080', changeOrigin: true },
    },
  },
});
