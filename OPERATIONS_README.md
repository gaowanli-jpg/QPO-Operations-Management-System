# Operations

## Capacity report

Run a one-time capacity report:

```powershell
cd D:\Code
powershell -ExecutionPolicy Bypass -File .\scripts\capacity-report.ps1
```

Output:
- `capacity-reports/<timestamp>/capacity-report.txt`
- `capacity-reports/<timestamp>/capacity-report.json`

The report includes:
- host disk usage
- database size
- largest PostgreSQL tables
- local backup and archive directory size
- Docker volume usage for PostgreSQL and uploaded files

## Monthly archive

Run a one-time monthly archive:

```powershell
cd D:\Code
powershell -ExecutionPolicy Bypass -File .\scripts\archive-monthly.ps1
```

Default behavior:
- exports `audit_log` rows older than 180 days to CSV, then deletes them from the live database
- exports old order candidates older than 730 days to CSV only
- does not delete historical order data automatically

Output:
- `archives/<timestamp>/db/audit_log_before_180_days.csv`
- `archives/<timestamp>/db/old_order_candidates_before_730_days.csv`
- `archives/<timestamp>/meta/archive.json`

Dry run:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\archive-monthly.ps1 -DryRun
```

## Install monthly archive task

```powershell
cd D:\Code
powershell -ExecutionPolicy Bypass -File .\scripts\install-archive-task.ps1 -MonthlyAt "03:00" -DayOfMonth 1
```

This creates Windows Task Scheduler task `QuoteSystemMonthlyArchive`.

Disable it:

```powershell
Unregister-ScheduledTask -TaskName QuoteSystemMonthlyArchive -Confirm:$false
```

View it:

```powershell
Get-ScheduledTask -TaskName QuoteSystemMonthlyArchive
schtasks /Query /TN QuoteSystemMonthlyArchive
```
