# 04 Design Tokens

The current `quote-frontend/src/style.css` already has useful ERP tokens. The redesign should expand them into a full token system.

## Recommended Token Files

Future structure:

```text
quote-frontend/src/styles/
├── tokens.css        # ERP semantic tokens
├── element-plus.css  # Element Plus component overrides
├── layout.css        # shell/page layout classes
├── components.css    # reusable ERP component classes
└── utilities.css     # small helpers
```

Until this split is implemented, keep using `quote-frontend/src/style.css` as the source of truth.

## Color Tokens

Recommended base:

```css
:root {
  --erp-bg: #f5f7fa;
  --erp-surface: #ffffff;
  --erp-surface-muted: #f8fafc;
  --erp-surface-soft: #eef3f8;

  --erp-border: #d8e0e8;
  --erp-border-subtle: #e6ebf0;
  --erp-border-strong: #c4cfda;

  --erp-text: #202a35;
  --erp-text-secondary: #667789;
  --erp-text-muted: #8a98a8;

  --erp-primary: #1769aa;
  --erp-primary-hover: #0f4f82;
  --erp-accent: #f3c44f;

  --erp-success: #20724f;
  --erp-warning: #9a5b13;
  --erp-danger: #b42318;

  --erp-rail: #172331;
  --erp-rail-soft: #223247;
}
```

Rules:

- Blue is the only primary action color.
- Amber is attention/accent, not a second primary.
- Green is success/completion.
- Red is destructive/failure.
- Gray is structure and hierarchy.

## Typography

Recommended:

```css
--erp-font-sans: "Segoe UI", "Microsoft YaHei", Arial, sans-serif;
--erp-font-size-xs: 12px;
--erp-font-size-sm: 13px;
--erp-font-size-md: 14px;
--erp-font-size-lg: 16px;
--erp-font-size-xl: 20px;
--erp-line-height-tight: 1.35;
--erp-line-height-base: 1.55;
```

Rules:

- Body text: 14px.
- Table text: 13px or 14px depending on density.
- Page title: 20px, 600-700 weight.
- Section title: 16px, 600-700 weight.
- Labels: 13px or 14px, 500-600 weight.
- Use tabular numbers for prices, quantities, and percentages where possible.

## Spacing

Use a 4/8px rhythm:

```css
--erp-space-1: 4px;
--erp-space-2: 8px;
--erp-space-3: 12px;
--erp-space-4: 16px;
--erp-space-5: 20px;
--erp-space-6: 24px;
--erp-space-8: 32px;
```

Rules:

- Page padding: 24px desktop, 16px narrow screens.
- Section gap: 16-24px.
- Form item gap: 12-16px.
- Button group gap: 8px.
- Table action gap: 8px.

## Radius

```css
--erp-radius-sm: 4px;
--erp-radius-md: 6px;
--erp-radius-lg: 8px;
--erp-radius-xl: 12px;
```

Rules:

- Buttons and inputs: 6-8px.
- Cards and panels: 8px.
- Modals: 8-12px.
- Avoid overly pill-shaped controls unless they are tags.

## Shadow / Elevation

ERP should use low elevation.

```css
--erp-shadow-none: none;
--erp-shadow-sm: 0 1px 2px rgba(20, 32, 45, 0.06);
--erp-shadow-md: 0 8px 24px rgba(20, 32, 45, 0.08);
```

Rules:

- Default panels use borders, not heavy shadows.
- Dialogs and drawers can use `--erp-shadow-md`.
- Avoid random per-page shadow values.

## Element Plus Mapping

Map ERP tokens to Element Plus variables:

```css
:root {
  --el-color-primary: var(--erp-primary);
  --el-border-radius-base: var(--erp-radius-lg);
  --el-font-family: var(--erp-font-sans);
}
```

Component overrides should stay in one place, not inside every page.

