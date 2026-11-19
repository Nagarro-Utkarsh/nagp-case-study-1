# HabitLoop frontend

SvelteKit (Svelte 5) static SPA, Stencil component package and typed API client in a pnpm workspace.

## Requirements

- Node.js 22 (see `.nvmrc`)
- pnpm 10.30.1 (Corepack is recommended)
- The HabitLoop backend from `../backend` for live API flows

## Local development

```sh
corepack enable
pnpm install
pnpm dev
```

The web app is available at `http://localhost:5173`. Its Vite proxy routes the service paths to backend ports 8081–8086, so start the backend with `docker compose up --build` from `../backend`. Set `VITE_API_BASE` only when using a separate API origin; it is public build-time configuration and must never contain a secret.

The UI package builds a `dist-custom-elements` bundle for SvelteKit/Vite, avoiding runtime lazy-chunk paths that do not work with the static SPA build.

## Checks and production build

```sh
pnpm check
pnpm test
pnpm build
```

The web build is written to `apps/web/build/` for private S3 and CloudFront hosting. It uses `adapter-static` with an `index.html` fallback and disables SSR. Configure CloudFront to rewrite SPA document routes to the fallback while excluding assets and `/api/*`; forward API methods, authorization/content headers and the SSE query string, disable API caching and protect SSE query strings from logs.

## Main demo

1. Sign up in the `public` or `acme` workspace and finish onboarding with a daily or weekly habit.
2. Log a habit on the dashboard and verify the activity summary refreshes. Open the dashboard in another tab to see SSE-driven updates.
3. Connect the mock wearable to a habit. Send a valid event, resend it to verify duplicate handling, and send an invalid event to inspect its rejected status.
4. Create a community and challenge. Invite an existing same-workspace account by email, open the challenge-context invite link as that account, accept community membership, then join the challenge with a habit whose category matches.
5. Record at least five distinct valid completions in a recent UTC hour and wait for analytics to catch up. Apply any timing recommendation shown.

Activity is persisted before analytics is necessarily updated. Dashboard statistics, recommendations and leaderboard progress may take a short time to catch up; unavailable leaderboard counts are shown as unavailable rather than zero. The browser stores a JWT for this assignment-grade SPA and sends it in the SSE query string; never log or publish either the JWT or the one-time wearable API key.
