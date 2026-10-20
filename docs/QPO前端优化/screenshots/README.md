# QPO UI Screenshot Baseline

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

Final browser walkthrough screenshots:

- `final-login.png`
- `final-market-dashboard.png`
- `final-quote-process-rfq174.png`
- `final-market-release-rfq174.png`
- `final-engineering-setup.png`
- `final-item-pricing-rfq174.png`
- `final-order-management.png`
- `final-history-order-management.png`
- `final-material-process-management.png`
- `final-user-management.png`
- `final-quote-template-management.png`

These images were generated after the Stage 1 foundation pass using the local Microsoft Edge executable with a temporary Playwright package from the npm cache. No browser binary or screenshot dependency was added to the project.

The in-app browser runtime still fails in this environment with a runtime property conflict, so future automated visual checks can reuse the local Edge fallback when needed.

Final walkthrough note:

- Final screenshots were captured from the Vite hot-start frontend at `http://localhost:5173`, not the Docker frontend image.
- Docker QPO backend and PostgreSQL were used as the data source.
- RFQ `174` / `RFQ-20260612-0002` was used for data-rich pricing and release screenshots.
- `final-quote-process-rfq174.png` uses RFQ 174 item data in a browser-only visual state because the local database currently contains only `已生成提单` orders, while the quote-process API intentionally accepts only `已报价` orders.
