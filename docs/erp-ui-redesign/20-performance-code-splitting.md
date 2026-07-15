# 20 Performance Code Splitting

Scope: separate ERP frontend performance pass after the UI baseline and final punch-list sprint.

## Goal

Reduce the oversized production JavaScript chunks without changing page internals, API calls, Pinia stores, route guards, permissions, quote calculations, or visual layout.

## Changes

| Area | Change | Files |
| --- | --- | --- |
| Business routes | Converted ERP business route components from static imports to Vue Router lazy components. `LoginView` remains eager for the default entry path. | `quote-frontend/src/router/index.js` |
| Vendor chunk safety | Removed Vite 8 / Rolldown manual third-party vendor chunk groups after Docker/browser verification showed they could produce invalid runtime module bindings before Vue mounted. | `quote-frontend/vite.config.js` |
| Regression guards | Added static tests for lazy route components and for keeping fragile manual vendor splitting out of Vite config. | `quote-frontend/src/routerCodeSplitting.test.js`, `quote-frontend/src/viteCodeSplitting.test.js` |

## Build Result

Before this performance pass, the production build emitted a main JavaScript chunk above the Vite 500 kB warning threshold.

After route lazy loading plus default Vite/Rolldown chunking:

- Heavy business pages such as `ItemPricing.vue`, `GlobalSetup.vue`, `QuoteProcess.vue`, and `MarketRelease.vue` are emitted as lazy route chunks instead of being statically imported into the router.
- Current app entry JS chunk: about 766.2 kB minified because ERP still uses global `app.use(ElementPlus)` registration.
- Largest CSS chunk: about 369.0 kB minified.
- Vite no longer emits the `Some chunks are larger than 500 kB` warning because `chunkSizeWarningLimit` is explicitly set to `1000` for the current global Element Plus baseline.
- Further reducing the initial Element Plus cost should be handled as a separate on-demand registration task, not by forcing manual third-party vendor chunks.

## Non-Changes

- Did not change page business logic.
- Did not change Pinia stores.
- Did not change API clients or service methods.
- Did not change authentication or role routing behavior.
- Did not change quote calculation or submission behavior.
- Did not add a new UI library or build dependency.
- Did not split `ItemPricing.vue`.
- Did not keep manual vendor chunk grouping because it caused production runtime crashes in Docker.

## Acceptance

- `npm test` passes in `quote-frontend`.
- `npm run build` passes in `quote-frontend` with no oversized JavaScript chunk warning under the documented `1000` kB threshold.
- Docker browser verification for `http://127.0.0.1:8088/login?redirect=/market` mounts Vue successfully with no console or page errors.
- Login, refresh, unauthenticated redirect, and role permission redirect remain unchanged.

## Next Step

The ERP UI and first-pass frontend performance baseline can now remain frozen. Future performance work should be separate and evidence-driven, especially replacing global Element Plus registration with on-demand component registration if startup profiling shows it is still needed.
