# ERP 备份与恢复说明

编写日期：2026-06-13  
适用系统：机加工报价 ERP 系统  
适用部署方式：Docker Compose  
当前项目根目录：`D:\Code`  

本文用于交接 ERP 的备份、恢复和恢复演练方法。ERP 的关键数据不只在数据库里，还包括上传的询价文件、图纸 PDF、报价单模板和后台任务输出。因此备份必须同时覆盖数据库和上传文件。

## 1. 当前备份恢复脚本

脚本目录：

```text
D:\Code\scripts
```

ERP 相关脚本：

| 脚本 | 用途 |
| --- | --- |
| `backup.ps1` | 手动备份 ERP 数据库和上传文件 |
| `run-backup-task.ps1` | Windows 定时任务调用的备份入口，写入备份日志 |
| `install-backup-task.ps1` | 安装 Windows 每日自动备份任务 |
| `restore.ps1` | 将指定备份恢复到 ERP 当前环境，破坏性操作，必须带 `-Force` |
| `restore-drill.ps1` | 启动临时 PostgreSQL 容器做恢复演练，不覆盖当前 ERP |
| `archive-monthly.ps1` | 月度归档脚本，导出旧审计日志和旧订单候选清单 |
| `install-archive-task.ps1` | 安装月度归档 Windows 任务 |

本次交接前已修正两个恢复相关问题：

1. `restore-drill.ps1` 原先还校验已删除的库存表 `inventory_master`，现在已改为校验当前 ERP 核心表。
2. `restore.ps1` 原先恢复到已有数据库卷时可能因表已存在失败，现在恢复前会重置目标数据库 `public` schema，并在复制上传文件前清空旧上传目录。

## 2. 备份内容

一次有效 ERP 备份目录结构如下：

```text
D:\Code\backups\
  yyyyMMdd_HHmmss\
    db\
      postgres.sql
    uploads\
      ...
    meta\
      backup.json
```

| 路径 | 内容 | 必须 |
| --- | --- | --- |
| `db\postgres.sql` | PostgreSQL 数据库 SQL dump | 是 |
| `uploads\` | 后端 `/data/uploads` 上传文件目录 | 是 |
| `meta\backup.json` | 备份元数据 | 建议必须 |

有效备份判断：

1. 备份目录名应符合 `yyyyMMdd_HHmmss`。
2. 必须存在 `db\postgres.sql`。
3. `postgres.sql` 文件大小必须大于 0。
4. `uploads` 目录应存在，即使当前没有上传文件。
5. `meta\backup.json` 应能说明备份时间、数据库名、数据库用户、项目路径等。

注意：如果某个备份目录只有 `db`、`uploads`、`meta` 空目录，但没有 `db\postgres.sql`，它不是有效备份，不能用于恢复。

## 3. 备份覆盖的数据

### 3.1 数据库

数据库备份通过 PostgreSQL 容器内 `pg_dump` 完成，当前默认数据库来自 `erp.env`：

| 变量 | 说明 |
| --- | --- |
| `POSTGRES_DB` | 数据库名 |
| `POSTGRES_USER` | 数据库用户 |

数据库中包含：

| 数据 | 表举例 |
| --- | --- |
| RFQ 订单 | `order_rfq` |
| 零件报价明细 | `item_quote_version` |
| 提单 | `order_delivery_ticket` |
| 工艺流转卡 | `order_process_sheet` |
| 材料和工艺字典 | `dict_material`、`dict_process` |
| 费用字典 | `dict_markup_fee`、`dict_extra_fee_item` |
| 用户和权限 | `erp_user` |
| 审计和任务记录 | `audit_log`、`system_task` |
| Flyway 迁移历史 | `flyway_schema_history` |

### 3.2 上传文件

上传文件来自后端容器：

```text
backend:/data/uploads
```

通常包含：

| 文件 | 说明 |
| --- | --- |
| 客户询价 Excel | 市场部导入时上传 |
| 图纸 PDF | 新询价导入或订单管理补图上传 |
| 报价单模板 | 管理员上传 |
| 其他上传资料 | 后续业务扩展文件 |

### 3.3 不包含的内容

普通备份不包含：

| 内容 | 说明 |
| --- | --- |
| Docker 镜像 | 可由 Dockerfile 重新构建 |
| 源码仓库 | 需要单独通过代码仓库或文件交接保存 |
| `erp.env` | 包含密钥，不建议直接混入备份目录 |
| 外层 Nginx HTTPS 证书 | 由部署人员单独维护 |
| 操作系统级计划任务配置 | 可通过脚本重新安装 |

## 4. 手动备份

### 4.1 前置条件

1. Docker 正常运行。
2. ERP Compose 服务正在运行，至少 `postgres` 和 `backend` 容器可访问。
3. `D:\Code\erp.env` 存在且配置正确。
4. 当前用户有执行 PowerShell 脚本和 Docker 命令的权限。

### 4.2 执行命令

```powershell
cd D:\Code
powershell -ExecutionPolicy Bypass -File .\scripts\backup.ps1 -ProjectRoot D:\Code
```

带清理旧备份：

```powershell
cd D:\Code
powershell -ExecutionPolicy Bypass -File .\scripts\backup.ps1 -ProjectRoot D:\Code -PruneOld -KeepDays 14
```

### 4.3 脚本实际做了什么

`backup.ps1` 会执行：

1. 读取 `erp.env` 中的数据库名和数据库用户。
2. 在 `D:\Code\backups` 下创建时间戳目录。
3. 在 PostgreSQL 容器内执行 `pg_dump`。
4. 将 dump 文件复制到 `db\postgres.sql`。
5. 从后端容器复制 `/data/uploads` 到备份目录。
6. 写入 `meta\backup.json`。
7. 如果带 `-PruneOld`，删除超过保留天数的旧备份目录。

### 4.4 成功标准

备份完成后应看到类似输出：

```text
Backup completed: D:\Code\backups\yyyyMMdd_HHmmss
```

并检查：

```powershell
Get-Item D:\Code\backups\yyyyMMdd_HHmmss\db\postgres.sql
Get-ChildItem D:\Code\backups\yyyyMMdd_HHmmss\uploads
Get-Content D:\Code\backups\yyyyMMdd_HHmmss\meta\backup.json
```

## 5. 自动备份

### 5.1 安装每日自动备份任务

默认任务名：

```text
QuoteSystemDailyBackup
```

默认执行时间：

```text
每天 02:00
```

安装命令：

```powershell
cd D:\Code
powershell -ExecutionPolicy Bypass -File .\scripts\install-backup-task.ps1 -ProjectRoot D:\Code -DailyAt 02:00 -KeepDays 14
```

### 5.2 自动备份日志

日志位置：

```text
D:\Code\backups\logs\scheduled-backup.log
```

查看日志：

```powershell
Get-Content D:\Code\backups\logs\scheduled-backup.log -Tail 100
```

### 5.3 查看计划任务

```powershell
Get-ScheduledTask -TaskName QuoteSystemDailyBackup
```

### 5.4 手动触发计划任务

```powershell
Start-ScheduledTask -TaskName QuoteSystemDailyBackup
```

触发后查看日志和 `D:\Code\backups` 下是否产生新的有效备份目录。

### 5.5 删除计划任务

如需重新安装或停用：

```powershell
Unregister-ScheduledTask -TaskName QuoteSystemDailyBackup -Confirm:$false
```

## 6. 恢复演练

恢复演练用于验证备份是否真的可用，不会覆盖当前 ERP 环境。

### 6.1 演练命令

```powershell
cd D:\Code
powershell -ExecutionPolicy Bypass -File .\scripts\restore-drill.ps1 -BackupDir D:\Code\backups\yyyyMMdd_HHmmss -ProjectRoot D:\Code
```

### 6.2 脚本实际做了什么

`restore-drill.ps1` 会执行：

1. 启动一个临时 PostgreSQL 容器。
2. 将备份里的 `db\postgres.sql` 复制到临时容器。
3. 执行 SQL dump 恢复。
4. 查询当前 ERP 核心表记录数。
5. 演练完成后自动删除临时容器。

当前校验的核心表：

| 表 | 说明 |
| --- | --- |
| `order_rfq` | RFQ 订单 |
| `item_quote_version` | 零件报价明细 |
| `order_delivery_ticket` | 交付提单 |
| `order_process_sheet` | 工艺流转卡 |
| `dict_material` | 材料字典 |
| `dict_process` | 工艺字典 |
| `erp_user` | 用户表 |

### 6.3 本次已验证记录

2026-06-13 已使用以下备份做过恢复演练：

```text
D:\Code\backups\20260527_080802
```

演练结果：成功。临时 PostgreSQL 容器中核心表可恢复并查询。

### 6.4 演练失败的常见原因

| 原因 | 处理 |
| --- | --- |
| `db\postgres.sql` 不存在 | 该目录不是有效备份，换一份有效备份 |
| Docker 未运行 | 启动 Docker 后重试 |
| PostgreSQL 镜像不存在且服务器不能联网 | 提前导入 `postgres:15` 镜像 |
| SQL dump 损坏 | 换用更早备份，检查备份脚本日志 |
| 校验表不存在 | 检查脚本是否与当前数据库结构一致 |

## 7. 正式恢复

正式恢复会覆盖当前 ERP 数据库和上传文件。该操作必须由部署或运维负责人执行。

### 7.1 恢复前确认

恢复前必须确认：

1. 已明确要恢复的备份目录。
2. 备份目录中存在 `db\postgres.sql`。
3. 已先对当前环境再做一次备份，保留恢复前现场。
4. 已通知业务人员停止使用 ERP。
5. 已确认恢复会覆盖当前数据库。
6. 已确认恢复会清空并替换当前上传文件目录。

建议先做恢复前备份：

```powershell
cd D:\Code
powershell -ExecutionPolicy Bypass -File .\scripts\backup.ps1 -ProjectRoot D:\Code
```

### 7.2 正式恢复命令

```powershell
cd D:\Code
powershell -ExecutionPolicy Bypass -File .\scripts\restore.ps1 -BackupDir D:\Code\backups\yyyyMMdd_HHmmss -ProjectRoot D:\Code -Force
```

必须带 `-Force`。不带 `-Force` 时脚本会直接拒绝执行。

### 7.3 脚本实际做了什么

`restore.ps1` 当前流程：

1. 校验 `-Force` 参数。
2. 校验备份目录存在。
3. 校验 `db\postgres.sql` 存在。
4. 读取 `erp.env` 中数据库配置。
5. 执行 `docker compose down` 停止 ERP。
6. 只启动 PostgreSQL 容器。
7. 将备份 SQL 复制到 PostgreSQL 容器。
8. 重置目标数据库 `public` schema。
9. 执行 SQL dump 恢复。
10. 删除容器内临时 SQL 文件。
11. 启动后端和前端。
12. 清空后端容器 `/data/uploads` 旧文件。
13. 将备份目录中的 `uploads` 复制回 `/data/uploads`。

### 7.4 恢复后检查

恢复完成后执行：

```powershell
cd D:\Code
docker compose --env-file erp.env ps
```

后端健康检查：

```powershell
curl http://127.0.0.1:8080/api/health
```

前端检查：

```text
http://127.0.0.1:8088
```

业务检查：

| 检查项 | 预期 |
| --- | --- |
| 登录 | 管理员可登录 |
| 订单列表 | 能看到恢复备份中的订单 |
| 图纸文件 | 已绑定图纸能打开 |
| 报价模板 | 模板导出功能正常 |
| 材料与工艺 | 字典数据存在 |
| 提单和流转卡 | 历史单据存在 |

## 8. 数据卷恢复注意事项

当前 `restore.ps1` 通过 SQL 覆盖数据库 schema，而不是删除 Docker volume。这样可以避免误删 volume，但仍然是破坏性恢复。

正式环境禁止随意执行：

```powershell
docker compose down -v
```

因为 `down -v` 会删除数据库和文件 volume。只有在明确要重建空库或灾难恢复方案明确要求时，才可以执行。

## 9. 备份保留策略

当前自动备份脚本支持：

```powershell
-PruneOld -KeepDays 14
```

含义：

1. 只清理 `D:\Code\backups` 下名称符合 `yyyyMMdd_HHmmss` 的目录。
2. 删除最后修改时间早于保留天数的目录。
3. 不会清理 `aps`、`aps-postgres`、`logs` 等非时间戳目录。

建议正式环境：

| 类型 | 建议 |
| --- | --- |
| 每日备份 | 本机保留 14 到 30 天 |
| 每周备份 | 异机或 NAS 保留 2 到 3 个月 |
| 每月归档 | 按企业要求保留 1 年以上 |
| 关键上线前备份 | 永久或长期保留 |

注意：本机备份不能替代异机备份。服务器硬盘损坏时，本机备份也可能一起丢失。

## 10. 月度归档

月度归档脚本：

```text
D:\Code\scripts\archive-monthly.ps1
```

作用：

1. 导出超过指定天数的审计日志到 CSV。
2. 导出超过指定天数的旧订单候选清单到 CSV。
3. 默认会删除超过保留期的审计日志。
4. 旧订单只导出候选清单，不自动删除订单。

安装月度归档任务：

```powershell
cd D:\Code
powershell -ExecutionPolicy Bypass -File .\scripts\install-archive-task.ps1 -ProjectRoot D:\Code -DayOfMonth 1 -MonthlyAt 03:00
```

归档目录：

```text
D:\Code\archives\yyyyMMdd_HHmmss
```

正式上线前，月度归档策略应由业务负责人确认，特别是审计日志保留天数和订单保留策略。

## 11. 灾难恢复建议流程

服务器故障或数据损坏时建议按以下顺序处理：

1. 暂停业务使用，避免继续写入错误数据。
2. 复制当前损坏环境的 `erp.env`、日志和可见数据目录，保留现场。
3. 找到最近一份有效备份。
4. 对该备份执行 `restore-drill.ps1`。
5. 如果演练通过，再执行正式恢复。
6. 恢复后检查登录、订单、图纸、核价、导出、提单。
7. 记录故障原因、恢复时间点、丢失数据范围。
8. 如涉及客户数据或生产数据，通知业务负责人确认。

## 12. 交接给运维人员的重点

1. ERP 有数据库备份和上传文件备份，缺一不可。
2. 有效备份必须包含 `db\postgres.sql`。
3. 自动备份任务需要在正式服务器安装并确认日志。
4. 恢复演练应定期做，至少上线前做一次。
5. 正式恢复必须先备份当前现场，再带 `-Force` 执行。
6. `erp.env` 不在普通备份范围内，应单独安全保管。
7. 不要随意执行 `docker compose down -v`。
8. 备份目录应定期复制到异机或 NAS。

