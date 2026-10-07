/// <reference types="vitest/config" />
import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// This config file runs in Node. Declared here rather than adding @types/node for two reads.
declare const process: { env: Record<string, string | undefined> };

export default defineConfig({
  plugins: [react()],
  build: {
    // Clinic PCs run older browsers. ES2017 output avoids syntax they cannot parse.
    target: 'es2017',
  },
  server: {
    port: Number(process.env.WEB_PORT ?? 5173),
    // Same-origin calls in development, as in production where the node serves both UI and API.
    // scripts/run.sh sets API_ORIGIN when it has moved the API off its default port.
    proxy: { '/api': process.env.API_ORIGIN ?? 'http://localhost:8080' },
  },
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: ['./src/test/setup.ts'],
    include: ['src/**/*.test.{ts,tsx}'],
    exclude: ['src/bench/**'],
  },
});
