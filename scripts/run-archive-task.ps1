param(
    [string]$ProjectRoot = "D:\Code",
    [int]$AuditLogKeepDays = 180,
    [int]$OrderCandidateDays = 730
)

$ErrorActionPreference = "Stop"

$logDir = Join-Path $ProjectRoot "archives\logs"
$logFile = Join-Path $logDir "monthly-archive.log"
New-Item -ItemType Directory -Force -Path $logDir | Out-Null

Start-Transcript -Path $logFile -Append | Out-Null
try {
    & (Join-Path $ProjectRoot "scripts\capacity-report.ps1") -ProjectRoot $ProjectRoot
    & (Join-Path $ProjectRoot "scripts\archive-monthly.ps1") `
        -ProjectRoot $ProjectRoot `
        -AuditLogKeepDays $AuditLogKeepDays `
        -OrderCandidateDays $OrderCandidateDays
}
catch {
    Write-Error $_
    exit 1
}
finally {
    Stop-Transcript | Out-Null
}
