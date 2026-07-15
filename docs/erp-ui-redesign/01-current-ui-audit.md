# 01 Current UI Audit

## Summary

The ERP frontend works functionally, but the visual system is not yet strict enough. The main issue is not one bad color or one bad component. The issue is that each page has grown its own local layout language.

## Current Strengths

- The app already uses Vue 3, Element Plus, Pinia, and centralized API helpers.
- `quote-frontend/src/style.css` already defines ERP color tokens such as `--erp-bg`, `--erp-surface`, `--erp-border`, and `--erp-primary`.
- The sidebar/header app shell exists in `quote-frontend/src/App.vue`.
- Element Plus gives us mature tables, dialogs, forms, buttons, uploaders, tags, tabs, and descriptions.

## Main Problems

### 1. Page Structure Is Inconsistent

Different pages organize headers, actions, forms, filters, and tables differently.

Examples:

- `MarketDashboard.vue` uses import/upload focused sections.
- `OrderManage.vue` has search, detail tabs, and supplement dialogs.
- `GlobalSetup.vue` has order context, drawing preview, configuration, repeat quote alerts.
- `ItemPricing.vue` has a very large mixed workbench with table, dialog, form panes, drawing preview, and pricing actions.

The result is that users need to relearn the page rhythm on each screen.

### 2. Repeated Local CSS Classes

Several pages define their own versions of similar ideas:

- `main-card`
- `card-header`
- `section-title`
- `full-width`
- `panel-heading`
- `form-box`
- `upload-box`
- `pagination-wrap`

These should become shared page and component patterns, not one-off local styles.

### 3. Data-Dense Pages Need Better Hierarchy

ERP pages often need dense information. The issue is not density itself. The problem is insufficient hierarchy:

- primary action vs secondary action is not always obvious;
- page title, current order context, and process step are not consistently separated;
- table-heavy screens need clearer filter/action/data zones;
- complex forms need stronger grouping and progressive disclosure.

### 4. App Shell Has a Useful Base but Needs Polish

`App.vue` already has:

- dark sidebar;
- top header;
- user account actions;
- role-based navigation.

It needs a stricter navigation hierarchy:

- shorter, cleaner menu labels;
- active route indicator;
- clear module grouping;
- header that shows current workflow context instead of generic system status.

### 5. Visual Tokens Exist but Are Too Shallow

Current `style.css` has basic colors and Element Plus overrides, but lacks:

- spacing scale;
- radius scale;
- elevation scale;
- typography roles;
- status surface tokens;
- table density tokens;
- page layout tokens.

Without these, each page keeps inventing spacing, card structure, and action layout.

## Immediate Cleanup Targets

1. Establish a shared page shell pattern.
2. Establish table/filter/action section patterns.
3. Normalize buttons, dialogs, cards, tags, and messages.
4. Move from local page-specific visual decisions to shared design tokens.
5. Refactor `ItemPricing.vue` visually in phases, not as one huge rewrite.

