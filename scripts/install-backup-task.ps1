param(
    [string]$ProjectRoot = "D:\Code",
    [string]$TaskName = "QuoteSystemDailyBackup",
    [string]$DailyAt = "02:00",
    [int]$KeepDays = 14
)

$backupScript = Join-Path $ProjectRoot "scripts\backup.ps1"
if (-not (Test-Path $backupScript)) {
    throw "找不到备份脚本：$backupScript"
}

$action = New-ScheduledTaskAction `
    -Execute "powershell.exe" `
    -Argument "-NoProfile -ExecutionPolicy Bypass -File `"$backupScript`" -ProjectRoot `"$ProjectRoot`" -PruneOld -KeepDays $KeepDays"

$trigger = New-ScheduledTaskTrigger -Daily -At $DailyAt
$settings = New-ScheduledTaskSettingsSet -StartWhenAvailable -MultipleInstances IgnoreNew

Register-ScheduledTask `
    -TaskName $TaskName `
    -Action $action `
    -Trigger $trigger `
    -Settings $settings `
    -Description "Quote system database and upload files daily backup" `
    -Force
