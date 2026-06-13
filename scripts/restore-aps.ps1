param(
    [Parameter(Mandatory = $true)]
    [string]$BackupDir,
    [string]$ProjectRoot = "D:\Code",
    [string]$ComposeFile = "",
    [string]$EnvFile = "",
    [switch]$Force
)

$ErrorActionPreference = "Stop"

function Read-DotEnv {
    param([string]$Path)
    $values = @{}
    if (-not (Test-Path -LiteralPath $Path)) {
        return $values
    }
    foreach ($line in Get-Content -LiteralPath $Path) {
        $text = $line.Trim()
        if (-not $text -or $text.StartsWith("#") -or -not $text.Contains("=")) {
            continue
        }
        $parts = $text.Split("=", 2)
        $values[$parts[0].Trim()] = $parts[1].Trim().Trim('"').Trim("'")
    }
    return $values
}

function Resolve-Setting {
    param([hashtable]$EnvValues, [string]$Name, [string]$DefaultValue)
    $processValue = [Environment]::GetEnvironmentVariable($Name, "Process")
    if ($processValue) {
        return $processValue
    }
    if ($EnvValues.ContainsKey($Name) -and $EnvValues[$Name]) {
        return $EnvValues[$Name]
    }
    return $DefaultValue
}

if (-not $Force) {
    throw "APS restore is destructive. Re-run with -Force after confirming the target environment can be overwritten."
}

$ProjectRoot = (Resolve-Path $ProjectRoot).Path
if (-not $ComposeFile) {
    $ComposeFile = Join-Path $ProjectRoot "docker-compose.aps.yml"
}
if (-not $EnvFile) {
    $EnvFile = Join-Path $ProjectRoot "aps.env"
}
$ComposeFile = (Resolve-Path $ComposeFile).Path
$EnvFile = (Resolve-Path $EnvFile).Path

if (-not (Test-Path -LiteralPath $BackupDir)) {
    throw "Backup directory does not exist: $BackupDir"
}

$dbDump = Join-Path $BackupDir "db\aps.sql"
$metaFile = Join-Path $BackupDir "meta\backup.json"
if (-not (Test-Path -LiteralPath $dbDump)) {
    throw "APS database backup file not found: $dbDump"
}

$envValues = Read-DotEnv $EnvFile
$dbUser = Resolve-Setting $envValues "APS_POSTGRES_USER" "aps"
$dbName = Resolve-Setting $envValues "APS_POSTGRES_DB" "aps"
if (Test-Path -LiteralPath $metaFile) {
    $meta = Get-Content -LiteralPath $metaFile -Raw | ConvertFrom-Json
    if ($meta.databaseUser) {
        $dbUser = $meta.databaseUser
    }
    if ($meta.database) {
        $dbName = $meta.database
    }
}

Push-Location $ProjectRoot
try {
    $compose = @("--env-file", $EnvFile, "-f", $ComposeFile)

    docker compose @compose stop aps-backend aps-frontend
    docker compose @compose up -d aps-postgres

    $ready = $false
    for ($i = 0; $i -lt 30; $i++) {
        docker compose @compose exec -T aps-postgres pg_isready -U $dbUser -d $dbName | Out-Null
        if ($LASTEXITCODE -eq 0) {
            $ready = $true
            break
        }
        Start-Sleep -Seconds 2
    }
    if (-not $ready) {
        throw "APS PostgreSQL container is not ready."
    }

    $containerDump = "/tmp/aps-restore.sql"
    docker compose @compose cp $dbDump "aps-postgres:$containerDump" | Out-Null
    if ($LASTEXITCODE -ne 0) {
        throw "Failed to copy APS backup into PostgreSQL container."
    }

    $resetSql = "drop schema if exists public cascade; create schema public; grant all on schema public to public;"
    docker compose @compose exec -T aps-postgres psql -v ON_ERROR_STOP=1 -U $dbUser -d $dbName -c $resetSql
    if ($LASTEXITCODE -ne 0) {
        throw "Failed to reset APS database schema."
    }

    docker compose @compose exec -T aps-postgres psql -v ON_ERROR_STOP=1 -U $dbUser -d $dbName -f $containerDump
    if ($LASTEXITCODE -ne 0) {
        throw "APS database restore failed."
    }

    docker compose @compose exec -T aps-postgres rm -f $containerDump | Out-Null
    docker compose @compose up -d aps-backend aps-frontend

    Write-Host "APS restore completed from: $BackupDir"
}
finally {
    Pop-Location
}
