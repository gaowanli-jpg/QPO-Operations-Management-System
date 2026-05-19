param(
    [string]$ProjectRoot = "D:\Code",
    [string]$BackupRoot = (Join-Path "D:\Code" "backups"),
    [string]$ComposeFile = (Join-Path "D:\Code" "docker-compose.yml"),
    [switch]$PruneOld,
    [int]$KeepDays = 14
)

$timestamp = Get-Date -Format "yyyyMMdd_HHmmss"
$runDir = Join-Path $BackupRoot $timestamp
$dbDir = Join-Path $runDir "db"
$uploadsDir = Join-Path $runDir "uploads"
$metaDir = Join-Path $runDir "meta"

New-Item -ItemType Directory -Force -Path $dbDir, $uploadsDir, $metaDir | Out-Null

Push-Location $ProjectRoot
try {
    $compose = @("-f", $ComposeFile)
    docker compose @compose exec -T postgres pg_dump -U postgres postgres |
        Set-Content -Encoding utf8 (Join-Path $dbDir "postgres.sql")

    docker compose @compose cp backend:/data/uploads/. $uploadsDir | Out-Null

    $meta = [ordered]@{
        backupTime = (Get-Date).ToString("o")
        projectRoot = $ProjectRoot
        composeFile = $ComposeFile
        database = "postgres"
        uploadsContainerPath = "/data/uploads"
    }
    $meta | ConvertTo-Json -Depth 5 | Set-Content -Encoding utf8 (Join-Path $metaDir "backup.json")

    if ($PruneOld) {
        Get-ChildItem $BackupRoot -Directory |
            Where-Object { $_.Name -match '^\d{8}_\d{6}$' -and $_.LastWriteTime -lt (Get-Date).AddDays(-$KeepDays) } |
            Remove-Item -Recurse -Force
    }
}
finally {
    Pop-Location
}
