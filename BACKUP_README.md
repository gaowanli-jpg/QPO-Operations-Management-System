# Backup Flow

## Manual backup
```powershell
cd D:\Code
powershell -ExecutionPolicy Bypass -File .\scripts\backup.ps1
```

## Prune old backups
```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\backup.ps1 -PruneOld -KeepDays 14
```

## Restore from a backup
```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\restore.ps1 -BackupDir "D:\Code\backups\20260517_020000"
```

## Install daily scheduled backup
```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\install-backup-task.ps1 -DailyAt "02:00"
```

## Backup contents
- `db/postgres.sql`: PostgreSQL logical backup
- `uploads/`:图纸和上传文件
- `meta/backup.json`: backup metadata
