# 07 Page Patterns

## Market Import Center

Applies to:

- `MarketDashboard.vue`

Pattern:

```text
Page Header
Upload / Folder Selection Surface
File Summary
Import Result / Error Feedback
```

Design focus:

- make upload state clear;
- show selected files and counts visibly;
- keep submit action obvious;
- separate import result from upload input.

## Quote Process / Market Release

Applies to:

- `QuoteProcess.vue`
- `MarketRelease.vue`

Pattern:

```text
Page Header
RFQ Context
Document / Ticket / Process Sheet Tabs
Action Toolbar
Main Table
Dialog for creation/editing
```

Design focus:

- make RFQ context persistent;
- separate quote export actions from destructive actions;
- keep ticket/process sheet generation grouped.

## Engineering Global Setup

Applies to:

- `GlobalSetup.vue`

Pattern:

```text
Page Header
Selected Order Context
Drawing Preview / Actions
Global Config Form
Repeat Quote Suggestions
Save / Continue Action
```

Design focus:

- show selected order context as a stable summary panel;
- keep drawing preview visually tied to the order;
- group material, shape, route, heat/surface treatment;
- make "save and continue to item pricing" the main action.

## Item Pricing Workbench

Applies to:

- `ItemPricing.vue`

Pattern:

```text
Page Header
RFQ / Item Context
Item Table
Pricing Workbench Dialog or Split Pane
Material Section
Process Section
Extra Fees Section
Pricing Summary
Submit / Complete Actions
```

Design focus:

- do not attempt one-shot beautification;
- first standardize shell, table, toolbar, and dialog;
- then split repeated visual blocks into shared styles;
- preserve calculation logic and Pinia/global config behavior.

## Order Management

Applies to:

- `OrderManage.vue`
- `HistoryOrderManage.vue`

Pattern:

```text
Page Header
Filter Bar
Order Table
Order Detail Panel / Tabs
Supplement Drawing Dialog
Danger Zone Actions
```

Design focus:

- filters should be compact and aligned;
- detail sections need consistent title and spacing;
- supplement drawing flow needs clear upload feedback;
- delete/reset actions should be separated.

## Dictionary Management

Applies to:

- `MaterialProcessManage.vue`

Pattern:

```text
Page Header
Dictionary Tabs
Active Dictionary Toolbar
Editable Table / Dialog
Save/Delete Feedback
```

Design focus:

- tabs must feel like one dictionary module;
- forms should be compact;
- save/delete refresh should feel immediate;
- status and disabled entries should be visually distinct.

## Admin Pages

Applies to:

- `UserManage.vue`
- `QuoteTemplateManage.vue`

Pattern:

```text
Page Header
Toolbar
Table / Template Surface
Dialog
Audit Feedback
```

Design focus:

- admin pages should be calmer and more utilitarian;
- destructive actions require strong confirmation;
- template upload/download needs a clear state model.

