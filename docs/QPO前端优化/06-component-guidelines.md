# 06 Component Guidelines

These guidelines define how pages should use Element Plus and shared QPO styles.

## Page Header

Use on every business page.

Contents:

- title;
- short description;
- optional status tags;
- primary page action.

Avoid:

- multiple competing primary buttons;
- page titles buried inside cards;
- inconsistent title sizes.

## Toolbar / Filter Bar

Use for search, customer filters, RFQ selectors, date filters, and batch actions.

Rules:

- filters left, actions right;
- primary action last on the right;
- destructive batch actions visually separated;
- wrap cleanly on smaller screens.

## Tables

QPO tables should be dense but legible.

Rules:

- header background uses `--erp-surface-soft`;
- row hover uses a subtle blue-gray;
- numeric columns right-aligned;
- action columns fixed or visually separated;
- status columns use tags, not raw colored text;
- long text uses tooltip or secondary line.

Recommended table shell:

```html
<section class="erp-table-surface">
  <div class="erp-table-header">...</div>
  <el-table>...</el-table>
  <div class="erp-pagination">...</div>
</section>
```

## Forms

Rules:

- visible labels, never placeholder-only;
- group related fields;
- keep required fields obvious;
- show error near field when possible;
- use helper text for complex fields like route, material, drawing, template.

Form density:

- simple admin form: compact;
- quote/pricing form: grouped and progressive;
- upload/import form: explanatory but not marketing-like.

## Cards / Panels

Use cards only when they frame a real unit of work.

Rules:

- no cards inside cards unless it is a repeated record item;
- avoid decorative card grids;
- prefer `erp-surface` / `erp-section` classes for major page areas.

## Buttons

Rules:

- one primary button per section;
- secondary actions use default or plain style;
- destructive actions use danger and require confirmation;
- loading state required for async submit;
- icon buttons need labels or tooltips.

## Dialogs

Rules:

- title states the action, not vague text like "提示";
- body explains consequence for destructive actions;
- footer has cancel left/first and primary action right/last;
- destructive confirmation uses danger button;
- long dialogs should have clear internal sections.

## Tags And Status

Use semantic mapping:

- success: completed, enabled, valid;
- warning: pending, attention, missing data;
- danger: failed, blocked, destructive;
- info: neutral metadata.

Do not rely on color alone. Status should include text.

## Loading / Empty / Error

Every async surface needs:

- loading state;
- empty state;
- error state with recovery action where possible.

Examples:

- "暂无订单，先导入新单。"
- "字典加载失败，重试。"
- "图纸预览失败，下载或重新打开。"

