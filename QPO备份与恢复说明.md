# QPO备份与恢复说明
## Workspace Boundary

This document only covers QPO backup and restore. APS is maintained separately; QPO backup, restore drills, and scheduled tasks no longer cover APS project files or APS-only data.

## 手动备份
```powershell
cd D:\QPO-Operations-Management-System
powershell -ExecutionPolicy Bypass -File .\scripts\backup.ps1
```

## 清理旧备份
```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\backup.ps1 -PruneOld -KeepDays 14
```

## 从备份恢复
恢复会覆盖目标Docker数据库，所以必须显式添加 `-Force`。

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\restore.ps1 -BackupDir "D:\QPO-Operations-Management-System\backups\20260517_020000" -Force
```

## 不影响业务库的恢复演练
```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\restore-drill.ps1 -BackupDir "D:\QPO-Operations-Management-System\backups\20260517_020000"
```

## 安装每日定时备份
```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\install-backup-task.ps1 -DailyAt "02:00"
```

## 备份内容
- `db/postgres.sql`：PostgreSQL逻辑备份。
- `uploads/`：图纸和上传文件。
- `meta/backup.json`：备份元数据。

脚本会优先从 `erp.env` 读取 `POSTGRES_USER`、`POSTGRES_DB`、`POSTGRES_PASSWORD`，读取不到时才使用本地开发默认值。
