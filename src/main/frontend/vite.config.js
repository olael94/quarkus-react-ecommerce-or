import react from '@vitejs/plugin-react';
import { defineConfig } from 'vite';

export default defineConfig({
  plugins: [react()],
  test: {
    globals: true,
    environment: 'jsdom',
    // jsdom's default test origin is the opaque "about:blank", which has no
    // working localStorage - a real origin here means CartContext's tests
    // (and anything else touching localStorage) work against jsdom's actual
    // implementation instead of needing a hand-rolled polyfill.
    environmentOptions: {
      jsdom: {
        url: 'http://localhost:3000',
      },
    },
    setupFiles: './src/setupTests.js',
  },
});
