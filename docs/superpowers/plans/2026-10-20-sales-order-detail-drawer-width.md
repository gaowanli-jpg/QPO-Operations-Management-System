# Sales Order Detail Drawer Width Implementation Plan

> **For agentic workers:** Execute this plan inline in the current session. Check off each step after it is completed.

**Goal:** Make the sales-order detail drawer start at the effective sidebar edge and fill the remaining viewport width.

**Architecture:** `App.vue` remains the source of truth for whether the sidebar is collapsed and provides that reactive state to routed page components. `SalesOrderManage.vue` applies a collapsed-state class to the teleported Element Plus drawer; CSS custom properties set the drawer offset to 200px or 72px, with a 760px media rule forcing 72px on narrow screens. The drawer width is `calc(100vw - sidebar width)`.

**Tech Stack:** Vue 3 `<script setup>`, Element Plus Drawer, component-scoped CSS, Vitest, npm.

## Global Constraints

- Preserve existing detail content, scrolling, close behavior, permissions, API calls, and business operations.
- Do not change sidebar widths, collapse behavior, or responsive breakpoints.
- Do not change the drawer's vertical sizing, modal behavior, or dismissal rules.
- Do not add a new UI library or alter permission or audit behavior.
- Do not change backend, store, permission, or logging behavior for this layout-only adjustment.

---

## File Map

- `quote-frontend/src/App.vue`: provides the existing reactive sidebar-collapsed state to descendants.
- `quote-frontend/src/components/SalesOrderManage.vue`: binds the drawer's size and edge to that state and defines responsive drawer width variables.

## Task 1: Bind the Sales-Order Drawer to the Sidebar

**Files:**
- Modify: `quote-frontend/src/App.vue`
- Modify: `quote-frontend/src/components/SalesOrderManage.vue`

**Interfaces:**
- `App.vue` provides the existing `isSidebarCollapsed` ref with the injection key `'isSidebarCollapsed'`.
- `SalesOrderManage.vue` injects that ref and applies `sales-order-detail-drawer--collapsed` while it is true.

- [ ] **Step 1: Provide the existing sidebar state from the application shell**

In `quote-frontend/src/App.vue`, add `provide` to the Vue imports and provide the existing ref after its declaration:

```js
import { computed, provide, reactive, ref } from 'vue'
provide('isSidebarCollapsed', isSidebarCollapsed)
```

Add the `provide` call immediately after the existing `isSidebarCollapsed` declaration.

Keep `sidebarWidth` and the sidebar markup unchanged.

- [ ] **Step 2: Set the drawer width and edge from its responsive CSS variable**

In `quote-frontend/src/components/SalesOrderManage.vue`, import `inject`, inject the shell state, and replace the drawer's fixed `76%` size:

```js
import { computed, inject, onMounted, reactive, ref, watch } from 'vue'
```

Add this declaration after the existing Vue imports:

```js
const isSidebarCollapsed = inject('isSidebarCollapsed', ref(false))
```

```vue
<el-drawer
  v-model="detailVisible"
  title="销售订单详情"
  class="sales-order-detail-drawer"
  :class="{ 'sales-order-detail-drawer--collapsed': isSidebarCollapsed }"
  size="calc(100vw - var(--sales-order-detail-sidebar-width))"
>
```

Add the following rules to the existing scoped style block. The class selector targets Element Plus's drawer element; the media rule appears after the collapsed selector so a narrow viewport always resolves to 72px.

```css
:global(.el-drawer.sales-order-detail-drawer) {
  --sales-order-detail-sidebar-width: 200px;
  left: var(--sales-order-detail-sidebar-width);
  right: auto;
}

:global(.el-drawer.sales-order-detail-drawer--collapsed) {
  --sales-order-detail-sidebar-width: 72px;
}

@media (max-width: 760px) {
  :global(.el-drawer.sales-order-detail-drawer) {
    --sales-order-detail-sidebar-width: 72px;
  }
}
```

- [ ] **Step 3: Review the actual diff for scope and responsive behavior**

Run: `git diff --check`

Expected: no whitespace errors. Confirm the only implementation files changed are `App.vue` and `SalesOrderManage.vue`, and that drawer width resolves from `calc(100vw - var(--sales-order-detail-sidebar-width))`.

- [ ] **Step 4: Run frontend tests and production build**

Run from `quote-frontend`:

```powershell
npm test
npm run build
```

Expected: Vitest exits successfully and Vite produces a successful production build.

- [ ] **Step 5: Verify the drawer in the browser**

At a desktop viewport, open a sales-order detail and verify its left edge matches the sidebar right edge at 200px. Collapse the sidebar while the drawer is open and verify the left edge moves to 72px while the drawer still reaches the viewport's right edge. At a viewport width of 760px or less, verify the offset remains 72px in both sidebar states. Confirm drawer scrolling and closing still work and the page has no horizontal overflow.

- [ ] **Step 6: Commit the implementation**

```powershell
git add -- quote-frontend/src/App.vue quote-frontend/src/components/SalesOrderManage.vue
git commit -m "fix: align sales order detail drawer with sidebar"
```
