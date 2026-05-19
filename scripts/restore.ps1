param(
    [Parameter(Mandatory = $true)]
    [string]$BackupDir,
    [string]$ProjectRoot = "D:\Code",
    [string]$ComposeFile = "",
    [switch]$Force
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
        $values[$parts[0].Trim()] = $parts[1].Trim().Trim('"').Trim("'")
    }
    return $values
}

function Resolve-Setting {
    param([hashtable]$EnvFile, [string]$Name, [string]$DefaultValue)
    $processValue = [Environment]::GetEnvironmentVariable($Name, "Process")
    if ($processValue) {
        return $processValue
    }
    if ($EnvFile.ContainsKey($Name) -and $EnvFile[$Name]) {
        return $EnvFile[$Name]
    }
    return $DefaultValue
}

if (-not $Force) {
    throw "Restore is destructive. Re-run with -Force after confirming the target environment can be overwritten."
}

$ProjectRoot = (Resolve-Path $ProjectRoot).Path
if (-not $ComposeFile) {
    $ComposeFile = Join-Path $ProjectRoot "docker-compose.yml"
}
if (-not (Test-Path $BackupDir)) {
    throw "Backup directory does not exist: $BackupDir"
}

$dbDump = Join-Path $BackupDir "db\postgres.sql"
$uploadsDir = Join-Path $BackupDir "uploads"
$metaFile = Join-Path $BackupDir "meta\backup.json"
if (-not (Test-Path $dbDump)) {
    throw "Database backup file not found: $dbDump"
}

$envFile = Read-DotEnv (Join-Path $ProjectRoot ".env")
$dbUser = Resolve-Setting $envFile "POSTGRES_USER" "postgres"
$dbName = Resolve-Setting $envFile "POSTGRES_DB" "postgres"
if (Test-Path $metaFile) {
    $meta = Get-Content $metaFile -Raw | ConvertFrom-Json
    if ($meta.databaseUser) {
        $dbUser = $meta.databaseUser
    }
    if ($meta.database) {
        $dbName = $meta.database
    }
}

Push-Location $ProjectRoot
try {
    docker compose -f $ComposeFile down
    docker compose -f $ComposeFile up -d postgres
    Start-Sleep -Seconds 10

    $containerDump = "/tmp/quote-system-restore.sql"
    docker compose -f $ComposeFile cp $dbDump "postgres:$containerDump" | Out-Null
    if ($LASTEXITCODE -ne 0) {
        throw "Failed to copy database backup into PostgreSQL container."
    }

    docker compose -f $ComposeFile exec -T postgres psql -v ON_ERROR_STOP=1 -U $dbUser -d $dbName -f $containerDump
    if ($LASTEXITCODE -ne 0) {
        throw "Database restore failed."
    }
    docker compose -f $ComposeFile exec -T postgres rm -f $containerDump | Out-Null

    docker compose -f $ComposeFile up -d backend frontend

    if (Test-Path $uploadsDir) {
        docker compose -f $ComposeFile cp "$uploadsDir/." "backend:/data/uploads" | Out-Null
    }

    Write-Host "Restore completed from: $BackupDir"
}
finally {
    Pop-Location
}
