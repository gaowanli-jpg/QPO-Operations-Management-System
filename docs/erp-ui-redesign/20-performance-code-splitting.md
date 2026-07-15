# 20 Performance Code Splitting

Scope: separate ERP frontend performance pass after the UI baseline and final punch-list sprint.

## Goal

Reduce the oversized production JavaScript chunks without changing page internals, API calls, Pinia stores, route guards, permissions, quote calculations, or visual layout.

## Changes

| Area | Change | Files |
| --- | --- | --- |
| Business routes | Converted ERP business route components from static imports to Vue Router lazy components. `LoginView` remains eager for the default entry path. | `quote-frontend/src/router/index.js` |
| Shared vendor chunks | Added Vite 8 / Rolldown `build.rolldownOptions.output.codeSplitting.groups` for Vue, Element Plus, Element Plus icons, and utility dependencies. | `quote-frontend/vite.config.js` |
| Element Plus chunk size | Split `element-plus/es/components/*` into component-level `vendor-ep-*` chunks so the previous monolithic Element Plus vendor chunk no longer exceeds the Vite warning threshold. | `quote-frontend/vite.config.js` |
| Regression guards | Added static tests for lazy route components and Vite vendor chunk configuration. | `quote-frontend/src/routerCodeSplitting.test.js`, `quote-frontend/src/viteCodeSplitting.test.js` |

## Build Result

Before this performance pass, the production build emitted a main JavaScript chunk above the Vite 500 kB warning threshold.

After route lazy loading plus Rolldown vendor grouping:

- Main app JS chunk: about 17.5 kB minified.
- Largest JS chunk: about 188.3 kB minified.
- Largest CSS chunk: about 351.4 kB minified.
- Vite no longer emits the `Some chunks are larger than 500 kB` warning.

## Non-Changes

- Did not change page business logic.
- Did not change Pinia stores.
- Did not change API clients or service methods.
- Did not change authentication or role routing behavior.
- Did not change quote calculation or submission behavior.
- Did not add a new UI library or build dependency.
- Did not split `ItemPricing.vue`.

## Acceptance

- `npm test` passes in `quote-frontend`.
- `npm run build` passes in `quote-frontend` with no oversized JavaScript chunk warning.
- Login, refresh, unauthenticated redirect, and role permission redirect remain unchanged.

## Next Step

The ERP UI and first-pass frontend performance baseline can now be frozen. Future performance work should be separate and evidence-driven, such as replacing global Element Plus registration with on-demand component registration only if startup profiling shows it is still needed.
