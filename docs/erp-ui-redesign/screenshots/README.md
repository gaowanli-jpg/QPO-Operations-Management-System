# ERP UI Screenshot Baseline

Stage 0 screenshot targets:

- `stage0-login.png`
- `stage0-app-shell-market.png`
- `stage0-market-import.png`
- `stage0-engineering-setup.png`
- `stage0-item-pricing.png`
- `stage0-order-management.png`
- `stage0-material-process-management.png`

Stage 2 visual checks:

- `stage2-app-shell-market.png`
- `stage2-app-shell-pricing-active.png`

Stage 3 visual checks:

- `stage3-order-management.png`
- `stage3-material-process-management.png`

These images were generated after the Stage 1 foundation pass using the local Microsoft Edge executable with a temporary Playwright package from the npm cache. No browser binary or screenshot dependency was added to the project.

The in-app browser runtime still fails in this environment with a runtime property conflict, so future automated visual checks can reuse the local Edge fallback when needed.
