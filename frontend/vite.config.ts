import { defineConfig } from 'vite';
import vue from '@vitejs/plugin-vue';
export default defineConfig({
  plugins: [vue()],
  // Polices en fichiers, jamais en data: (CSP font-src 'self').
  build: {
    assetsInlineLimit: (file) => (/\.(woff2?|ttf)$/.test(file) ? false : undefined),
    // Pas de source maps ni de noms de modules (PlatformApp, NearbyMap…) dans les fichiers servis.
    sourcemap: false,
    rollupOptions: {
      output: {
        entryFileNames: 'assets/[hash].js',
        chunkFileNames: 'assets/[hash].js',
        assetFileNames: 'assets/[hash][extname]',
      },
    },
  },
  // Ni console ni debugger, ni commentaires de licence dans le bundle de production.
  esbuild: { drop: ['console', 'debugger'], legalComments: 'none' },
  server: {
    port: 5173,
    proxy: {
      '/api': { target: process.env.API_TARGET || 'http://127.0.0.1:18080', changeOrigin: true },
    },
  },
});
