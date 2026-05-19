param(
    [string]$ProjectRoot = "D:\Code",
    [string]$TaskName = "QuoteSystemMonthlyArchive",
    [string]$MonthlyAt = "03:00",
    [int]$DayOfMonth = 1,
    [int]$AuditLogKeepDays = 180,
    [int]$OrderCandidateDays = 730
)

$ErrorActionPreference = "Stop"

$ProjectRoot = (Resolve-Path $ProjectRoot).Path
$taskScript = Join-Path $ProjectRoot "scripts\run-archive-task.ps1"
if (-not (Test-Path $taskScript)) {
    throw "Archive task runner not found: $taskScript"
}

$taskRun = "powershell.exe -NoProfile -ExecutionPolicy Bypass -File `"$taskScript`" -ProjectRoot `"$ProjectRoot`" -AuditLogKeepDays $AuditLogKeepDays -OrderCandidateDays $OrderCandidateDays"
schtasks /Create `
    /TN $TaskName `
    /SC MONTHLY `
    /D $DayOfMonth `
    /ST $MonthlyAt `
    /TR $taskRun `
    /F | Out-Null

if ($LASTEXITCODE -ne 0) {
    throw "Failed to install monthly archive task."
}

Write-Host "Monthly archive task installed: $TaskName day $DayOfMonth at $MonthlyAt"
