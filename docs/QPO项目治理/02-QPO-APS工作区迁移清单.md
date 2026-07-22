# QPO / APS 工作区迁移清单

生成时间：2026-07-22。

## 1. 快照文件

- `migration-files-before.txt`：迁移前 Git 跟踪文件完整快照。
- `migration-path-references-before.txt`：迁移前关键路径引用扫描结果。

## 2. 归属统计

- QPO 文件：367 个。
- APS 文件：195 个。
- 混合/治理/待拆分文件：103 个。
- 当前根目录文件夹：aps-frontend、aps-system、backups、capacity-reports、deploy、docs、logs、quote-frontend、quote-system、scripts、材料与工艺费用表、交接文档、图纸、用例表格资料。

## 3. QPO 保留范围

- `quote-system/`：阶段 1 重命名为 `quote-backend/`。
- `quote-frontend/`。
- `docker-compose.yml`、`erp.env`、`erp.env.example`。
- `docs/QPO*/` 与本 `docs/QPO项目治理/`。
- QPO 运维文档：`QPO部署说明.md`、`QPO运维说明.md`、`QPO备份与恢复说明.md`。
- QPO 脚本：`scripts/backup.ps1`、`scripts/restore.ps1`、`scripts/restore-drill.ps1`、`scripts/install-backup-task.ps1`、`scripts/run-backup-task.ps1`、`scripts/capacity-report.ps1`。

## 4. QPO 业务资料保留范围

- `图纸\`：保留在 QPO 主目录（当前存在：True）
- `材料与工艺费用表\`：保留在 QPO 主目录（当前存在：True）
- `用例表格资料\`：保留在 QPO 主目录（当前存在：True）

## 5. APS 迁出范围

- `aps-system/`。
- `aps-frontend/`。
- `docker-compose.aps.yml`、`aps.env`、`aps.env.example`。
- `APS上线验收清单.md`、`APS部署说明.md`、`start-aps-backend.ps1`。
- APS 脚本：`scripts/*-aps.ps1`。
- APS 专项设计文档：`docs/superpowers/specs/*aps*.md`。

## 6. 混合/待拆分范围

- `AGENTS.md`：阶段 1 先更新为当前混合仓库口径，阶段 4 再改为 QPO 专用口径。
- `.gitignore`：阶段 2/3 根据两个工作区分别保留。
- `交接文档/`：包含 APS 运维资料，阶段 2 迁出时按文件内容拆分。
- `backups/`、`logs/`、`capacity-reports/`：可能包含运行产物，阶段 2 按 QPO/APS 子目录归属拆分。
- `migration-files-before.txt`、`migration-path-references-before.txt`：本次迁移护栏快照，保留在当前提交中便于审计。

## 7. 阶段 1 立即修改点

- `quote-system/` 使用 `git mv` 改名为 `quote-backend/`。
- `docker-compose.yml` 的 backend build context 改为 `./quote-backend`。
- QPO 相关文档、脚本、测试中的 `quote-system` 路径改为 `quote-backend`。
- APS 相关文件中的 `quote-system` 历史表述本阶段不批量迁移；阶段 2 迁出 APS 时处理。
