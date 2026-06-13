param(
    [string]$ProjectRoot = "D:\Code",
    [int]$KeepDays = 14
)

$ErrorActionPreference = "Stop"

$logDir = Join-Path $ProjectRoot "backups\aps\logs"
$logFile = Join-Path $logDir "scheduled-aps-backup.log"
New-Item -ItemType Directory -Force -Path $logDir | Out-Null

Start-Transcript -Path $logFile -Append | Out-Null
try {
    & (Join-Path $ProjectRoot "scripts\backup-aps.ps1") -ProjectRoot $ProjectRoot -PruneOld -KeepDays $KeepDays
}
catch {
    Write-Error $_
    exit 1
}
finally {
    Stop-Transcript | Out-Null
}
