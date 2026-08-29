import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// Build output lands in the backend's static resources (repo-root layout) so
// one Spring Boot fat JAR serves the SPA and the API (same origin, no CORS).
export default defineConfig({
  plugins: [react()],
  build: {
    outDir: '../src/main/resources/static',
    emptyOutDir: true,
  },
  server: {
    port: 5173,
    proxy: {
      '/api': 'http://localhost:8080',
      '/actuator': 'http://localhost:8080',
    },
  },
});
