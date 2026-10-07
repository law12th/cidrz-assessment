import { defineConfig } from 'vitest/config';
import react from '@vitejs/plugin-react';

// Runs only the measurement script (npm run measure), never part of the normal test run.
export default defineConfig({
  plugins: [react()],
  test: {
    environment: 'jsdom',
    include: ['src/bench/**/*.measure.tsx'],
    // Rendering 10,000 rows in jsdom is the thing being measured, and it takes far longer than
    // Vitest's 5s default. Without this the script prints its numbers and then reports a failure.
    testTimeout: 180_000,
  },
});
