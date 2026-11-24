import tailwindcss from '@tailwindcss/vite';
import react from '@vitejs/plugin-react';
import path from 'node:path';
import { defineConfig } from 'vite';

export default defineConfig({
  base: '/assets/webmap/',
  publicDir: false,
  plugins: [react(), tailwindcss()],
  define: {
    // Force Leaflet + OSM/CARTO inside the APK WebView so it never hits domain-locked Google Maps keys
    'import.meta.env.VITE_GOOGLE_MAPS_API_KEY': JSON.stringify(''),
  },
  resolve: {
    alias: {
      '@': path.resolve(__dirname, '.'),
    },
  },
  build: {
    outDir: 'android/app/src/main/assets/webmap',
    emptyOutDir: true,
    sourcemap: false,
    rollupOptions: {
      input: path.resolve(__dirname, 'embed-map.html'),
    },
  },
});
