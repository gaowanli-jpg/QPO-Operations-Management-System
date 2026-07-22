param(
    [string]$ProjectRoot = "D:\Code",
    [string]$BackupRoot = "",
    [string]$ComposeFile = "",
    [string]$EnvFile = "",
    [switch]$PruneOld,
    [int]$KeepDays = 14
)

$ErrorActionPreference = "Stop"

function Read-DotEnv {
    param([string]$Path)
    $values = @{}
    if (-not (Test-Path $Path)) {
        return $values
    }

    Get-Content -Path $Path | ForEach-Object {
        $line = $_.Trim()
        if (-not $line -or $line.StartsWith("#") -or -not $line.Contains("=")) {
            return
        }
        $parts = $line.Split("=", 2)
        $key = $parts[0].Trim()
        $value = $parts[1].Trim().Trim('"').Trim("'")
        if ($key) {
            $values[$key] = $value
        }
    }
    return $values
}

function Resolve-Setting {
    param(
        [hashtable]$EnvFile,
        [string]$Name,
        [string]$DefaultValue
    )
    $processValue = [Environment]::GetEnvironmentVariable($Name, "Process")
    if ($processValue) {
        return $processValue
    }
    if ($EnvFile.ContainsKey($Name) -and $EnvFile[$Name]) {
        return $EnvFile[$Name]
    }
    return $DefaultValue
}

function Escape-ShSingle {
    param([string]$Value)
    return "'" + ($Value -replace "'", "'\''") + "'"
}

$ProjectRoot = (Resolve-Path $ProjectRoot).Path
if (-not $BackupRoot) {
    $BackupRoot = Join-Path $ProjectRoot "backups"
}
if (-not $ComposeFile) {
    $ComposeFile = Join-Path $ProjectRoot "docker-compose.yml"
}
if (-not $EnvFile) {
    $EnvFile = Join-Path $ProjectRoot "erp.env"
}
$EnvFile = (Resolve-Path $EnvFile).Path

$envValues = Read-DotEnv $EnvFile
$dbUser = Resolve-Setting $envValues "POSTGRES_USER" "postgres"
$dbName = Resolve-Setting $envValues "POSTGRES_DB" "postgres"

$timestamp = Get-Date -Format "yyyyMMdd_HHmmss"
$runDir = Join-Path $BackupRoot $timestamp
$dbDir = Join-Path $runDir "db"
$uploadsDir = Join-Path $runDir "uploads"
$metaDir = Join-Path $runDir "meta"
$dumpFile = Join-Path $dbDir "postgres.sql"

New-Item -ItemType Directory -Force -Path $dbDir, $uploadsDir, $metaDir | Out-Null

Push-Location $ProjectRoot
try {
    $compose = @("--env-file", $EnvFile, "-f", $ComposeFile)
    $containerDump = "/tmp/quote-backend-backup-$timestamp.sql"
    $dumpCommand = "pg_dump -U $(Escape-ShSingle $dbUser) -d $(Escape-ShSingle $dbName) > $(Escape-ShSingle $containerDump)"

    docker compose @compose exec -T postgres sh -lc $dumpCommand
    if ($LASTEXITCODE -ne 0) {
        throw "PostgreSQL backup failed."
    }

    docker compose @compose cp "postgres:$containerDump" $dumpFile | Out-Null
    if ($LASTEXITCODE -ne 0) {
        throw "Failed to copy PostgreSQL backup out of the container."
    }

    docker compose @compose exec -T postgres rm -f $containerDump | Out-Null

    if (-not (Test-Path $dumpFile) -or (Get-Item $dumpFile).Length -le 0) {
        throw "PostgreSQL backup file is empty: $dumpFile"
    }

    docker compose @compose cp "backend:/data/uploads/." $uploadsDir | Out-Null
    if ($LASTEXITCODE -ne 0) {
        throw "Failed to copy uploaded files from backend container."
    }

    $gitCommit = ""
    try {
        $gitCommit = (git -C $ProjectRoot rev-parse --short HEAD 2>$null)
    } catch {
        $gitCommit = ""
    }

    $meta = [ordered]@{
        backupTime = (Get-Date).ToString("o")
        projectRoot = $ProjectRoot
        composeFile = $ComposeFile
        envFile = $EnvFile
        database = $dbName
        databaseUser = $dbUser
        dbDump = "db/postgres.sql"
        uploads = "uploads"
        uploadsContainerPath = "/data/uploads"
        gitCommit = $gitCommit
    }
    $meta | ConvertTo-Json -Depth 5 | Set-Content -Encoding utf8 (Join-Path $metaDir "backup.json")

    if ($PruneOld) {
        Get-ChildItem $BackupRoot -Directory |
            Where-Object { $_.Name -match '^\d{8}_\d{6}$' -and $_.LastWriteTime -lt (Get-Date).AddDays(-$KeepDays) } |
            Remove-Item -Recurse -Force
    }

    Write-Host "Backup completed: $runDir"
}
finally {
    Pop-Location
}
