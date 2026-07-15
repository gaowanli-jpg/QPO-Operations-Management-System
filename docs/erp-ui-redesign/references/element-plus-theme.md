# Element Plus Theme Reference

Source:

- https://element-plus.org/en-US/guide/theming.html
- https://element-plus.org/en-US/guide/dark-mode

## Key Notes

- Element Plus supports large-scale theme changes through SCSS variables.
- Element Plus also exposes CSS variables that can be changed globally or per component.
- Component-level customization can use inline CSS variables, but project-wide design should prefer shared variables.
- Dark mode can be implemented with Element Plus dark CSS variables later if needed.

## Relevance To ERP

Because this ERP already uses Element Plus, the redesign should stay close to Element Plus instead of layering a second design system on top.

## Project Implication

Prefer:

```css
:root {
  --el-color-primary: var(--erp-primary);
}
```

Avoid:

```css
.some-page .el-button--primary {
  background: #123abc;
}
```

Element Plus overrides should live in a shared style file.

