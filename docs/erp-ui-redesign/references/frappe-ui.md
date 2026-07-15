# Frappe UI Reference

Sources:

- https://ui.frappe.io/docs/introduction
- https://ui.frappe.io/docs/data-fetching/resource

## Key Notes

- Frappe UI is Vue 3 based.
- It includes generic components such as Button, Link, and Dialog.
- It also includes utilities for server-side data fetching and mutation management.

## What To Borrow

- Treat loading, success, error, and mutation states as standard UI behavior.
- Keep dialogs and buttons consistent.
- Use reusable component patterns instead of page-local one-offs.

## What Not To Borrow

- Do not install Frappe UI into this project.
- Do not mix component libraries.

## ERP Design Lesson

Good ERP UX is not just visual styling. It is also consistent state handling:

- while loading;
- after saving;
- after deleting;
- when an API fails;
- when a file preview fails;
- when a workflow is incomplete.

