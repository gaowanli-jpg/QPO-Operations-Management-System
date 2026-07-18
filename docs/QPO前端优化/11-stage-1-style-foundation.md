# 11 Stage 1 Style Foundation

Date: 2026-07-15

Scope: QPO frontend visual foundation for `quote-frontend`.

Stage 1 changes only the global visual foundation and supporting documentation. It does not change business behavior.

## Files Changed

| File | Purpose |
| --- | --- |
| `../../AGENTS.md` | Root project memory now carries long-term QPO/APS boundaries, cadence, guardrails, and next-step rules. |
| `quote-frontend/src/style.css` | Expands QPO design tokens, maps Element Plus base variables, and introduces shared QPO layout/component classes. |
| `quote-frontend/src/styleFoundation.test.js` | Guards that the Stage 1 style foundation keeps required tokens, Element Plus mappings, and shared classes. |
| `docs/QPO前端优化/11-stage-1-style-foundation.md` | Records what Stage 1 changed and what Stage 2 should do next. |

## What Changed

### Token Foundation

`style.css` now defines token groups for:

- typography: font family, font sizes, line heights, and weights;
- color: page background, surfaces, borders, text, primary, success, warning, danger, info, rail, and accent;
- spacing: 4px/8px rhythm from `--erp-space-1` through `--erp-space-10`;
- radius: small through full-radius values;
- shadow: none, small, medium, and focus ring;
- z-index: base, sticky, dropdown, dialog, and toast;
- motion: fast/base/slow durations and shared easing.

### Element Plus Mapping

Element Plus basics now map back to QPO tokens in one place:

- primary/success/warning/danger/info colors;
- page, overlay, and fill backgrounds;
- text colors;
- borders and radius;
- table header, row hover, and table borders;
- card, input, select, textarea, button, dialog, tag, tabs, and scrollbar base styling.

### Shared QPO Classes

Stage 1 adds shared classes for future migrations:

- `erp-page`
- `erp-page-header`
- `erp-page-actions`
- `erp-toolbar`
- `erp-filter-bar`
- `erp-surface`
- `erp-section`
- `erp-table-surface`
- `erp-empty-state`
- `erp-dialog-footer`

These classes are available for Stage 2 and Stage 3, but Stage 1 does not force existing pages to use them yet.

## Behavior Preserved

Stage 1 did not modify:

- Pinia stores;
- API services;
- router permissions;
- authentication behavior;
- quotation calculations;
- pricing submission;
- page component templates.

## Verification

Targeted foundation test:

- `npm test -- styleFoundation.test.js`
- Result: 1 test file passed; 3 tests passed.

Full Stage 1 verification:

- `npm test`
- Result: 12 test files passed; 62 tests passed.
- `npm run build`
- Result: build passed; Vite transformed 1684 modules and built successfully in 1.10s.

Build output sizes:

| Asset | Size | Gzip |
| --- | ---: | ---: |
| `dist/index.html` | 0.46 kB | 0.29 kB |
| `dist/assets/index-CWTXzQJs.css` | 388.83 kB | 53.10 kB |
| `dist/assets/index-CSz2ievI.js` | 1,332.42 kB | 411.23 kB |

Known warning: the main JS chunk remains larger than 500 kB after minification. This was already present in Stage 0 and remains deferred.

## Stage 2 Recommendation

The next step is Stage 2: App Shell Polish.

Recommended scope:

1. Modify `quote-frontend/src/App.vue` and shared shell styles only.
2. Use the Stage 1 tokens instead of hardcoded shell colors where practical.
3. Clean sidebar hierarchy, active route state, header spacing, and user controls.
4. Keep route visibility, auth checks, password dialog behavior, and logout behavior unchanged.
5. Re-run `npm test` and `npm run build`.

Defer:

- migrating business pages to `erp-page` classes until Stage 3;
- redesigning `GlobalSetup.vue` until Stage 5;
- redesigning `ItemPricing.vue` until Stage 6;
- code splitting or bundle optimization until visual stages are stable.
