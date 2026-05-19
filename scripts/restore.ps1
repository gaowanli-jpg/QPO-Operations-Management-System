param(
    [Parameter(Mandatory = $true)]
    [string]$BackupDir,
    [string]$ProjectRoot = "D:\Code",
    [string]$ComposeFile = (Join-Path "D:\Code" "docker-compose.yml")
)

if (-not (Test-Path $BackupDir)) {
    throw "备份目录不存在：$BackupDir"
}

$dbDump = Join-Path $BackupDir "db\postgres.sql"
$uploadsDir = Join-Path $BackupDir "uploads"

if (-not (Test-Path $dbDump)) {
    throw "找不到数据库备份文件：$dbDump"
}

Push-Location $ProjectRoot
try {
    docker compose -f $ComposeFile down
    docker compose -f $ComposeFile up -d postgres

    Start-Sleep -Seconds 10

    Get-Content $dbDump | docker compose -f $ComposeFile exec -T postgres psql -U postgres -d postgres

    docker compose -f $ComposeFile up -d backend frontend

    if (Test-Path $uploadsDir) {
        docker compose -f $ComposeFile cp $uploadsDir/. backend:/data/uploads
    }
}
finally {
    Pop-Location
}
