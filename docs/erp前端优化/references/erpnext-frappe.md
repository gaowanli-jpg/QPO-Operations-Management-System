# ERPNext And Frappe Reference

Sources:

- https://github.com/frappe/erpnext
- https://github.com/frappe/frappe
- https://frappe.io/erpnext

## Key Notes

- ERPNext is an open-source ERP built on the Frappe framework.
- It covers core ERP domains such as accounting, inventory, manufacturing, CRM, projects, and HR.
- Its product direction is useful as a reference for dense business workflows.

## What To Borrow

- Task-first ERP screens.
- Strong list/form hierarchy.
- Stable module navigation.
- Record-centric workflows.
- Clear state and action placement.

## What Not To Borrow

- Do not copy Frappe framework internals.
- Do not move away from Vue + Element Plus.
- Do not copy ERPNext page structures without adapting to our quote/manufacturing workflow.

## ERP Design Lesson

ERP screens should optimize for repeated work:

- scan;
- filter;
- compare;
- edit;
- confirm;
- export;
- trace history.

Visual polish should support those actions, not distract from them.

