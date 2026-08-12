# QPO运维说明
## Workspace Boundary

This document only covers QPO operations. APS may appear only as historical compatibility context; QPO operations, backups, logs, and scheduled tasks no longer maintain APS project files.

## 容量报告

生成一次容量报告：

```powershell
cd D:\QPO-Operations-Management-System
powershell -ExecutionPolicy Bypass -File .\scripts\capacity-report.ps1
```

输出文件：
- `capacity-reports/<timestamp>/capacity-report.txt`
- `capacity-reports/<timestamp>/capacity-report.json`

报告包含：
- 宿主机磁盘使用情况。
- 数据库大小。
- PostgreSQL最大数据表。
- 本地备份目录大小。
- PostgreSQL和上传文件相关Docker卷使用情况。

## 日志与保留策略

操作日志保留策略已经迁移到系统页面：

```text
系统 > 操作日志 > 日志保留策略
```

后端文件日志、Postgres 慢 SQL、Docker 容器日志和操作日志保留策略详见：

```text
docs/QPO日志模块/03-日志运维说明.md
```

## Flyway迁移与Compose启动

本地开发和测试服务器部署前，优先使用受保护的 Compose 启动脚本：

```powershell
cd D:\QPO-Operations-Management-System
.\scripts\compose-up.ps1
```

新增数据库迁移不要手工猜版本号，使用：

```powershell
.\scripts\new-flyway-migration.ps1 -Name your_migration_name
```

原因：当前 QPO 已压缩为 `V1/V2` 新基线，但部分开发库可能仍保留压缩前 `V3/V4` Flyway 历史。`V3/V4` 不允许复用；脚本会同时检查仓库迁移文件和运行中 PostgreSQL 的 `flyway_schema_history`，避免后端在 Docker Compose 阶段因为迁移被跳过而 unhealthy。详细说明见 `docs/ops/flyway-migration-policy.md`。
