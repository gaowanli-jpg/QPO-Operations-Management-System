param(
    [string]$ProjectRoot = "",
    [string]$ContainerName = "quote-postgres",
    [switch]$SkipDatabase
)

$ErrorActionPreference = "Stop"
$FIRST_POST_BASELINE_VERSION = 5

if ([string]::IsNullOrWhiteSpace($ProjectRoot)) {
    $ProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
}

function Fail($Message) {
    Write-Error $Message
    exit 1
}

function Get-MigrationFiles($MigrationDir) {
    if (-not (Test-Path $MigrationDir)) {
        Fail "Flyway migration directory not found: $MigrationDir"
    }
    Get-ChildItem -Path $MigrationDir -Filter "V*.sql" -File | ForEach-Object {
        if ($_.Name -match '^V(\d+)__.+\.sql$') {
            [pscustomobject]@{
                Version = [int]$Matches[1]
                Name = $_.Name
                Path = $_.FullName
            }
        }
    } | Sort-Object Version, Name
}

function Get-ContainerState($Name) {
    $state = (& docker inspect -f '{{.State.Status}}' $Name 2>$null)
    if ($LASTEXITCODE -ne 0) { return $null }
    return ($state | Select-Object -First 1).Trim()
}

function Get-PostgresEnv($Name, $Key, $Fallback) {
    $value = (& docker exec $Name printenv $Key 2>$null)
    if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($value)) { return $Fallback }
    return ($value | Select-Object -First 1).Trim()
}

function Get-DatabaseMaxFlywayVersion($Name) {
    $state = Get-ContainerState $Name
    if ($state -ne "running") { return $null }

    $dbUser = Get-PostgresEnv $Name "POSTGRES_USER" "postgres"
    $dbName = Get-PostgresEnv $Name "POSTGRES_DB" "postgres"
    $query = "SELECT COALESCE(MAX(version::int), 0) FROM flyway_schema_history WHERE success AND version ~ '^[0-9]+$';"
    $value = (& docker exec $Name psql -U $dbUser -d $dbName -At -c $query 2>$null)
    if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($value)) { return $null }
    return [int](($value | Select-Object -First 1).Trim())
}

$migrationDir = Join-Path $ProjectRoot "quote-backend/src/main/resources/db/migration"
$migrations = @(Get-MigrationFiles $migrationDir)
if ($migrations.Count -eq 0) {
    Fail "No Flyway migrations found in $migrationDir"
}

$versions = @($migrations | ForEach-Object { $_.Version })
foreach ($required in @(1, 2)) {
    if ($versions -notcontains $required) {
        Fail "Required compressed baseline migration V$required is missing."
    }
}

$duplicates = $migrations | Group-Object Version | Where-Object { $_.Count -gt 1 }
if ($duplicates) {
    $details = $duplicates | ForEach-Object { "V$($_.Name): " + (($_.Group | ForEach-Object Name) -join ', ') }
    Fail "Duplicate Flyway migration versions are not allowed:`n$($details -join "`n")"
}

$reusedCompressedHistory = $migrations | Where-Object { $_.Version -gt 2 -and $_.Version -lt $FIRST_POST_BASELINE_VERSION }
if ($reusedCompressedHistory) {
    $details = $reusedCompressedHistory | ForEach-Object { $_.Name }
    Fail "Do not reuse compressed-history Flyway versions V3/V4. Use scripts/new-flyway-migration.ps1 to allocate the next version. Offending files: $($details -join ', ')"
}

$repoMax = ($versions | Measure-Object -Maximum).Maximum
if (-not $SkipDatabase) {
    $dbMax = Get-DatabaseMaxFlywayVersion $ContainerName
    if ($null -ne $dbMax -and $dbMax -gt $repoMax) {
        Fail "Running database Flyway history is at V$dbMax but repository max is V$repoMax. Add the next migration above the database max or rebuild the dev database intentionally before compose."
    }
}

Write-Host "Flyway migration check passed. repoMax=V$repoMax firstPostBaseline=V$FIRST_POST_BASELINE_VERSION"
