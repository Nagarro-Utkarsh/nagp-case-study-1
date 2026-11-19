import { sveltekit } from '@sveltejs/kit/vite';
import { defineConfig } from 'vitest/config';

const targets: Array<[string, number]> = [
  ['/api/auth', 8081],
  ['/api/users', 8081],
  ['/api/habits', 8082],
  ['/api/activities', 8082],
  ['/api/communities', 8083],
  ['/api/challenges', 8083],
  ['/api/invites', 8083],
  ['/api/integrations', 8084],
  ['/api/analytics', 8085],
  ['/api/recommendations', 8086]
];

export default defineConfig({
  plugins: [sveltekit()],
  server: {
    proxy: Object.fromEntries(targets.map(([path, port]) => [
      path,
      { target: `http://localhost:${port}`, changeOrigin: true }
    ]))
  },
  test: {
    include: ['src/**/*.test.ts']
  }
});
