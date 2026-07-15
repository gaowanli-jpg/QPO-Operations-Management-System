# 05 Layout And Navigation

## App Shell

Current shell:

- `quote-frontend/src/App.vue` owns sidebar, header, user actions, and password dialog.

Target shell:

```text
ErpAppShell
  Sidebar
  Header
  MainContent
  GlobalDialogs
```

The visual refactor can keep this in `App.vue` initially. A later cleanup can split it if the file becomes hard to maintain.

## Sidebar

Goals:

- short labels;
- clear grouping;
- strong active state;
- predictable role visibility;
- no long phrases that wrap or crowd icons.

Recommended groups:

```text
市场
  新单导入
  报价处理
  议价与提单

工程
  工程设置
  逐件核价
  材料与工艺

订单
  订单管理
  历史订单

系统
  用户权限
  报价模板
```

## Header

Current header says generic system status. Replace with useful context.

Target header should show:

- current page title or module;
- optional current order/RFQ context;
- user avatar/name/role;
- password change and logout.

Avoid placing page-specific primary actions in the global header. Keep actions inside the page header or toolbar.

## Page Frame

Each content page should follow:

```html
<section class="erp-page">
  <header class="erp-page-header">
    <div>
      <h1>Page Title</h1>
      <p>Short description or context.</p>
    </div>
    <div class="erp-page-actions">...</div>
  </header>

  <section class="erp-toolbar">...</section>
  <section class="erp-surface">...</section>
</section>
```

## Responsive Behavior

Initial target:

- desktop-first ERP workspace, works well at 1366px and 1440px;
- usable down to tablet width;
- avoid horizontal page scroll;
- large tables may scroll inside a table container, not the full page.

Mobile can be improved later, but do not block it:

- controls must wrap;
- toolbar buttons can collapse into groups;
- large forms should stack.

## Scroll Behavior

Rules:

- Main page scroll should remain predictable.
- Avoid nested vertical scroll regions unless necessary for a workbench.
- Fixed headers must not cover content.
- Dialogs should own their internal scroll only when content is long.

