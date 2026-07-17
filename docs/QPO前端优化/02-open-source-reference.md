# 02 Open-Source Reference

This redesign should borrow principles from good QPO/admin projects, not copy their code wholesale.

## Reference 1: ERPNext / Frappe

Source:

- https://github.com/frappe/erpnext
- https://github.com/frappe/frappe

Why it matters:

- ERPNext is a mature open-source QPO product with modules such as accounting, inventory, manufacturing, CRM, projects, and HR.
- It is a useful reference for how real QPO products organize dense business workflows.

What to borrow:

- QPO is task-first: screens should help users finish work, not showcase decoration.
- Forms and lists need strong hierarchy.
- Module navigation should be stable and predictable.
- Business objects should feel like records in a workflow, not isolated pages.

What not to borrow directly:

- Frappe/ERPNext's framework architecture is different from our Vue + Element Plus app.
- Do not copy Frappe-specific routing, server-rendered form behavior, or backend patterns.

## Reference 2: Frappe UI

Source:

- https://ui.frappe.io/docs/introduction
- https://ui.frappe.io/docs/data-fetching/resource

Why it matters:

- Frappe UI is Vue 3 based and includes generic components such as Button, Link, and Dialog.
- It also has utilities for server-side data fetching and mutations.

What to borrow:

- Treat async data loading and mutation states as first-class UI concerns.
- Use consistent button, dialog, and loading patterns.
- Keep UI components generic and reusable.

What not to borrow directly:

- We already use Element Plus, so we should not introduce Frappe UI as a second component library.

## Reference 3: vue-element-plus-admin

Source:

- https://element-plus-admin-doc.cn/guide/design
- https://github.com/kailong321200875/vue-element-plus-admin

Why it matters:

- It is close to our stack: Vue and Element Plus.
- Its docs describe a style organization where common styles live under `src/style/`, with files such as `theme.less`, `var.css`, and variables modules.

What to borrow:

- Split global design concerns into a clear style folder.
- Keep root CSS variables available everywhere.
- Put theme-level decisions in a small number of shared files.

Recommended equivalent for this project:

```text
quote-frontend/src/styles/
├── tokens.css
├── element-plus.css
├── layout.css
├── components.css
└── utilities.css
```

This can be phased in from the existing `quote-frontend/src/style.css`.

## Reference 4: vue-pure-admin

Source:

- https://github.com/pure-admin/vue-pure-admin

Why it matters:

- It is a modern Vue 3 + Vite + Element Plus + Pinia admin template.
- It demonstrates a contemporary admin shell, route-driven navigation, and full admin template discipline.

What to borrow:

- Route-aware navigation.
- Cleaner admin shell structure.
- Layout separation between app shell and page content.
- Responsive admin considerations.

What not to borrow directly:

- Do not adopt Tailwind just for visual polish.
- Do not copy its full framework-level structure unless we intentionally plan a larger migration.

## Reference 5: Element Plus Theme System

Source:

- https://element-plus.org/en-US/guide/theming.html
- https://element-plus.org/en-US/guide/dark-mode

Why it matters:

- Our QPO already uses Element Plus.
- Element Plus supports SCSS variables and CSS variables for theme customization.

What to borrow:

- Prefer semantic CSS variables and Element Plus variables over ad-hoc page CSS.
- Use `--el-color-primary`, component CSS vars, and erp-specific tokens consistently.
- If dark mode is introduced later, use Element Plus' CSS variable direction instead of hand-writing a parallel theme.

## Design Files To Inspect During Implementation

External references:

- ERPNext: module/list/form patterns in the `erpnext` and `frappe` repositories.
- Frappe UI: docs for Button, Dialog, Resource, and Form Control patterns.
- vue-element-plus-admin: `src/style/` organization and theme variable conventions.
- vue-pure-admin: layout shell, route menus, tabs, theme configuration.
- Element Plus: theming guide, dark mode guide, component CSS variable docs.

Internal files:

- `quote-frontend/src/style.css`
- `quote-frontend/src/App.vue`
- `quote-frontend/src/components/GlobalSetup.vue`
- `quote-frontend/src/components/ItemPricing.vue`
- `quote-frontend/src/components/MarketDashboard.vue`
- `quote-frontend/src/components/OrderManage.vue`
- `quote-frontend/src/components/MaterialProcessManage.vue`

