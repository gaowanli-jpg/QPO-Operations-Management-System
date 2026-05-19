param(
    [string]$ProjectRoot = "D:\Code",
    [string]$TaskName = "QuoteSystemDailyBackup",
    [string]$DailyAt = "02:00",
    [int]$KeepDays = 14
)

$ErrorActionPreference = "Stop"

$ProjectRoot = (Resolve-Path $ProjectRoot).Path
$taskScript = Join-Path $ProjectRoot "scripts\run-backup-task.ps1"
$logDir = Join-Path $ProjectRoot "backups\logs"
$logFile = Join-Path $logDir "scheduled-backup.log"

if (-not (Test-Path $taskScript)) {
    throw "Backup task runner not found: $taskScript"
}
New-Item -ItemType Directory -Force -Path $logDir | Out-Null

$action = New-ScheduledTaskAction `
    -Execute "powershell.exe" `
    -Argument "-NoProfile -ExecutionPolicy Bypass -File `"$taskScript`" -ProjectRoot `"$ProjectRoot`" -KeepDays $KeepDays"

$trigger = New-ScheduledTaskTrigger -Daily -At $DailyAt
$settings = New-ScheduledTaskSettingsSet `
    -StartWhenAvailable `
    -MultipleInstances IgnoreNew `
    -ExecutionTimeLimit (New-TimeSpan -Hours 3)

Register-ScheduledTask `
    -TaskName $TaskName `
    -Action $action `
    -Trigger $trigger `
    -Settings $settings `
    -Description "Quote system database and upload files daily backup" `
    -Force | Out-Null

Write-Host "Scheduled backup task installed: $TaskName daily at $DailyAt"
Write-Host "Log file: $logFile"
