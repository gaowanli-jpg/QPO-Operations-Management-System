# 03 Design Direction

## Product Personality

The QPO should feel like a professional manufacturing quotation and operations workspace.

Keywords:

- precise;
- calm;
- structured;
- data-dense;
- trustworthy;
- fast to scan;
- low decoration;
- strong workflow hierarchy.

Avoid:

- marketing-style hero layouts;
- oversized decorative cards;
- gradient-heavy dashboards;
- playful colors;
- dark glassmorphism;
- single-page visual experiments that do not scale to all modules.

## Visual Direction

Use a neutral, work-focused interface:

- dark left navigation rail;
- white and very light gray content surfaces;
- blue primary action;
- amber warning/accent for quote or attention states;
- green success and red danger semantics;
- low shadow, stronger borders, crisp table grids.

This matches a manufacturing QPO better than a colorful SaaS dashboard because users spend time comparing values, editing forms, uploading drawings, and validating quote outputs.

## Information Hierarchy

Each page should follow this top-to-bottom order:

1. Page header: module title, short description, current workflow context.
2. Primary action row: the one action users most likely need.
3. Filters or order context.
4. Main data surface: table, form, workbench, or upload area.
5. Secondary details: logs, preview, history, auxiliary actions.

## Layout Tone

Use compact but readable density:

- tables can be dense;
- forms should be grouped;
- action bars should be consistent;
- empty states should explain the next action;
- destructive actions should be separated and confirmable.

## Preferred Page Model

```text
ErpPage
  ErpPageHeader
  ErpToolbar / ErpFilterBar
  ErpSection / ErpDataSurface
  Optional detail drawer/dialog/workbench
```

The page should not feel like many unrelated cards stacked together. It should feel like one workflow surface.

## Naming Direction

Use business language in UI:

- "报价工作台"
- "订单导入"
- "工程设置"
- "逐件核价"
- "材料与工艺字典"
- "历史订单"

Keep navigation labels short. Long labels make the sidebar noisy.

