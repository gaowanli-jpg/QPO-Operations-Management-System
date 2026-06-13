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

function Escape-ShSingle {
    param([string]$Value)
    return "'" + ($Value -replace "'", "'\''") + "'"
}

$ProjectRoot = (Resolve-Path $ProjectRoot).Path
if (-not $BackupRoot) {
    $BackupRoot = Join-Path $ProjectRoot "backups\aps"
}
if (-not $ComposeFile) {
    $ComposeFile = Join-Path $ProjectRoot "docker-compose.aps.yml"
}
if (-not $EnvFile) {
    $EnvFile = Join-Path $ProjectRoot "aps.env"
}
$ComposeFile = (Resolve-Path $ComposeFile).Path
$EnvFile = (Resolve-Path $EnvFile).Path

$envValues = Read-DotEnv $EnvFile
$dbUser = Resolve-Setting $envValues "APS_POSTGRES_USER" "aps"
$dbName = Resolve-Setting $envValues "APS_POSTGRES_DB" "aps"

$timestamp = Get-Date -Format "yyyyMMdd_HHmmss"
$runDir = Join-Path $BackupRoot $timestamp
$dbDir = Join-Path $runDir "db"
$metaDir = Join-Path $runDir "meta"
$dumpFile = Join-Path $dbDir "aps.sql"

New-Item -ItemType Directory -Force -Path $dbDir, $metaDir | Out-Null

Push-Location $ProjectRoot
try {
    $compose = @("--env-file", $EnvFile, "-f", $ComposeFile)
    $containerDump = "/tmp/aps-backup-$timestamp.sql"
    $dumpCommand = "pg_dump -U $(Escape-ShSingle $dbUser) -d $(Escape-ShSingle $dbName) > $(Escape-ShSingle $containerDump)"

    docker compose @compose exec -T aps-postgres sh -lc $dumpCommand
    if ($LASTEXITCODE -ne 0) {
        throw "APS PostgreSQL backup failed."
    }

    docker compose @compose cp "aps-postgres:$containerDump" $dumpFile | Out-Null
    if ($LASTEXITCODE -ne 0) {
        throw "Failed to copy APS PostgreSQL backup out of the container."
    }

    docker compose @compose exec -T aps-postgres rm -f $containerDump | Out-Null

    if (-not (Test-Path -LiteralPath $dumpFile) -or (Get-Item -LiteralPath $dumpFile).Length -le 0) {
        throw "APS PostgreSQL backup file is empty: $dumpFile"
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
        dbDump = "db/aps.sql"
        gitCommit = $gitCommit
    }
    $meta | ConvertTo-Json -Depth 5 | Set-Content -Encoding utf8 (Join-Path $metaDir "backup.json")

    if ($PruneOld) {
        Get-ChildItem $BackupRoot -Directory |
            Where-Object { $_.Name -match '^\d{8}_\d{6}$' -and $_.LastWriteTime -lt (Get-Date).AddDays(-$KeepDays) } |
            Remove-Item -Recurse -Force
    }

    Write-Host "APS backup completed: $runDir"
}
finally {
    Pop-Location
}
