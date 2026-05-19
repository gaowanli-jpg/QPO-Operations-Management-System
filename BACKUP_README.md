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
This overwrites the target Docker database, so it requires `-Force`.

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\restore.ps1 -BackupDir "D:\Code\backups\20260517_020000" -Force
```

## Restore drill without touching the business database
```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\restore-drill.ps1 -BackupDir "D:\Code\backups\20260517_020000"
```

## Install daily scheduled backup
```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\install-backup-task.ps1 -DailyAt "02:00"
```

## Backup contents
- `db/postgres.sql`: PostgreSQL logical backup
- `uploads/`: drawings and uploaded files
- `meta/backup.json`: backup metadata

The scripts read `POSTGRES_USER`, `POSTGRES_DB`, and `POSTGRES_PASSWORD` from `.env` first, then fall back to the local development defaults.
