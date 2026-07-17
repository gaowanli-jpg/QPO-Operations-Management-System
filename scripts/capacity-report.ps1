param(
    [string]$ProjectRoot = "D:\Code",
    [string]$OutputRoot = "",
    [string]$EnvFile = ""
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

function Get-DriveUsage {
    param([string]$RootPath)
    $drive = [System.IO.Path]::GetPathRoot($RootPath).TrimEnd('\').TrimEnd('/')
    if ([string]::IsNullOrWhiteSpace($drive)) {
        return $null
    }
    $driveLetter = $drive.Substring(0, 1)
    $psDrive = Get-PSDrive -Name $driveLetter -ErrorAction SilentlyContinue
    if (-not $psDrive) {
        return $null
    }
    $used = [double]$psDrive.Used
    $free = [double]$psDrive.Free
    $total = $used + $free
    if ($total -le 0) {
        return $null
    }
    [ordered]@{
        drive = $psDrive.Name
        usedBytes = [int64]$used
        freeBytes = [int64]$free
        totalBytes = [int64]$total
        usedPercent = [math]::Round(($used / $total) * 100, 2)
    }
}

function Convert-BytesToText {
    param([nullable[int64]]$Bytes)
    if ($null -eq $Bytes) {
        return $null
    }
    $value = [double]$Bytes
    foreach ($unit in @('B', 'KB', 'MB', 'GB', 'TB')) {
        if ($value -lt 1024 -or $unit -eq 'TB') {
            return ('{0:N2} {1}' -f $value, $unit)
        }
        $value = $value / 1024
    }
    return ('{0:N2} TB' -f $value)
}

function Get-DirectorySizeBytes {
    param([string]$Path)
    if (-not (Test-Path $Path)) {
        return 0
    }
    $sum = Get-ChildItem -Path $Path -Recurse -Force -File -ErrorAction SilentlyContinue |
        Measure-Object -Property Length -Sum
    if ($sum.Sum) {
        return [int64]$sum.Sum
    }
    return 0
}

function Invoke-PsqlText {
    param([string]$Sql, [object[]]$ComposeArgs, [string]$DbUser, [string]$DbName)
    $result = docker compose @ComposeArgs exec -T postgres psql -v ON_ERROR_STOP=1 -U $DbUser -d $DbName -t -A -c $Sql
    if ($LASTEXITCODE -ne 0) {
        throw "psql command failed."
    }
    return ($result -join "`n").Trim()
}

$ProjectRoot = (Resolve-Path $ProjectRoot).Path
if (-not $OutputRoot) {
    $OutputRoot = Join-Path $ProjectRoot "capacity-reports"
}
if (-not $EnvFile) {
    $EnvFile = Join-Path $ProjectRoot "erp.env"
}
$EnvFile = (Resolve-Path $EnvFile).Path
$envValues = Read-DotEnv $EnvFile
$composeFile = Join-Path $ProjectRoot "docker-compose.yml"
$compose = @("--env-file", $EnvFile, "-f", $composeFile)
$dbUser = Resolve-Setting $envValues "POSTGRES_USER" "postgres"
$dbName = Resolve-Setting $envValues "POSTGRES_DB" "postgres"

$timestamp = Get-Date -Format "yyyyMMdd_HHmmss"
$reportDir = Join-Path $OutputRoot $timestamp
New-Item -ItemType Directory -Force -Path $reportDir | Out-Null

$dbJsonSql = @"
select json_build_object(
  'generatedAt', now(),
  'database', current_database(),
  'databaseSizeBytes', pg_database_size(current_database()),
  'databaseSize', pg_size_pretty(pg_database_size(current_database())),
  'topTables', coalesce(
    (
      select json_agg(row_to_json(t))
      from (
        select
          schemaname,
          relname,
          n_live_tup,
          n_dead_tup,
          pg_total_relation_size((quote_ident(schemaname) || '.' || quote_ident(relname))::regclass) as size_bytes,
          pg_size_pretty(pg_total_relation_size((quote_ident(schemaname) || '.' || quote_ident(relname))::regclass)) as size_text
        from pg_stat_user_tables
        order by pg_total_relation_size((quote_ident(schemaname) || '.' || quote_ident(relname))::regclass) desc
        limit 10
      ) t
    ),
    '[]'::json
  )
)::text;
"@

$dbJsonText = Invoke-PsqlText -Sql $dbJsonSql -ComposeArgs $compose -DbUser $dbUser -DbName $dbName
$dbInfo = $dbJsonText | ConvertFrom-Json

$postgresVolumeSql = "select coalesce(sum(pg_total_relation_size((quote_ident(schemaname) || '.' || quote_ident(relname))::regclass)), 0)::bigint from pg_stat_user_tables;"
$postgresVolumeBytes = [int64](Invoke-PsqlText -Sql $postgresVolumeSql -ComposeArgs $compose -DbUser $dbUser -DbName $dbName)

$containerUsage = @{}
$postgresDu = docker compose @compose exec -T postgres sh -lc "du -sk /var/lib/postgresql/data 2>/dev/null | awk '{print `$1}'"
if ($LASTEXITCODE -eq 0 -and $postgresDu) {
    $containerUsage['postgresDataKb'] = [int64](($postgresDu | Select-Object -First 1).Trim())
}
$backendDu = docker compose @compose exec -T backend sh -lc "du -sk /data/uploads /data/backups 2>/dev/null"
if ($LASTEXITCODE -eq 0 -and $backendDu) {
    foreach ($line in $backendDu) {
        if ($line -match '^(\d+)\s+(/data/\S+)$') {
            $containerUsage[$matches[2]] = [int64]$matches[1]
        }
    }
}

$disk = Get-DriveUsage -RootPath $ProjectRoot
$backupsPath = Join-Path $ProjectRoot "backups"
$report = [ordered]@{
    generatedAt = (Get-Date).ToString("o")
    projectRoot = $ProjectRoot
    database = $dbInfo
    postgresRelationBytes = $postgresVolumeBytes
    postgresRelationText = Convert-BytesToText $postgresVolumeBytes
    disk = $disk
    localBackupsBytes = Get-DirectorySizeBytes $backupsPath
    localBackupsText = Convert-BytesToText (Get-DirectorySizeBytes $backupsPath)
    containerUsage = $containerUsage
}

$reportJson = $report | ConvertTo-Json -Depth 10
$reportJson | Set-Content -Encoding utf8 (Join-Path $reportDir "capacity-report.json")

$lines = New-Object System.Collections.Generic.List[string]
$lines.Add("Capacity Report")
$lines.Add("Generated: $($report.generatedAt)")
$lines.Add("Project root: $ProjectRoot")
if ($disk) {
    $lines.Add("Disk: $($disk.drive) used $($disk.usedPercent)% ($([Convert]::ToInt64($disk.usedBytes) )) / $([Convert]::ToInt64($disk.totalBytes)) bytes")
}
$lines.Add("Database: $($dbInfo.database) size $($dbInfo.databaseSize)")
$lines.Add("Postgres relation sum: $($report.postgresRelationText)")
$lines.Add("Local backups: $($report.localBackupsText)")
$lines.Add("")
$lines.Add("Top tables:")
foreach ($table in $dbInfo.topTables) {
    $lines.Add(" - $($table.relname): $($table.size_text), live=$($table.n_live_tup), dead=$($table.n_dead_tup)")
}
$lines | Set-Content -Encoding utf8 (Join-Path $reportDir "capacity-report.txt")

Write-Host "Capacity report written to: $reportDir"
Write-Host "Report text: $(Join-Path $reportDir 'capacity-report.txt')"
