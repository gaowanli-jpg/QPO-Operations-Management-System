# vue-element-plus-admin Reference

Sources:

- https://element-plus-admin-doc.cn/guide/design
- https://github.com/kailong321200875/vue-element-plus-admin

## Key Notes

- The project documents a style structure under `src/style/`.
- It separates theme, variable, and common style concerns.

## What To Borrow

- Create an explicit style system folder.
- Put root variables in a shared file.
- Keep theme and component overrides out of individual pages.

## Recommended Adaptation

Future ERP structure:

```text
quote-frontend/src/styles/
├── tokens.css
├── element-plus.css
├── layout.css
├── components.css
└── utilities.css
```

This should be phased in from the current `quote-frontend/src/style.css`.

## ERP Design Lesson

The most important admin-template lesson is style discipline:

- shared tokens;
- shared layout classes;
- shared component overrides;
- page CSS only for truly page-specific composition.

