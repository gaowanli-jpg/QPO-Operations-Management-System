param(
    [string]$ProjectRoot = "D:\Code",
    [string]$ArchiveRoot = "",
    [int]$AuditLogKeepDays = 180,
    [int]$OrderCandidateDays = 730,
    [switch]$DryRun
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

function Invoke-PsqlText {
    param([string]$Sql, [object[]]$ComposeArgs, [string]$DbUser, [string]$DbName)
    $result = docker compose @ComposeArgs exec -T postgres psql -v ON_ERROR_STOP=1 -U $DbUser -d $DbName -t -A -c $Sql
    if ($LASTEXITCODE -ne 0) {
        throw "psql command failed."
    }
    return ($result -join "`n").Trim()
}

function Invoke-Psql {
    param([string]$Sql, [object[]]$ComposeArgs, [string]$DbUser, [string]$DbName)
    docker compose @ComposeArgs exec -T postgres psql -v ON_ERROR_STOP=1 -U $DbUser -d $DbName -c $Sql
    if ($LASTEXITCODE -ne 0) {
        throw "psql command failed."
    }
}

function Export-QueryCsv {
    param(
        [string]$Sql,
        [string]$ContainerFile,
        [string]$HostFile,
        [object[]]$ComposeArgs,
        [string]$DbUser,
        [string]$DbName
    )
    $copySql = "COPY ($Sql) TO '$ContainerFile' WITH (FORMAT CSV, HEADER TRUE, ENCODING 'UTF8');"
    Invoke-Psql -Sql $copySql -ComposeArgs $ComposeArgs -DbUser $DbUser -DbName $DbName
    docker compose @ComposeArgs cp "postgres:$ContainerFile" $HostFile | Out-Null
    if ($LASTEXITCODE -ne 0) {
        throw "Failed to copy archive file from PostgreSQL container."
    }
    docker compose @ComposeArgs exec -T postgres rm -f $ContainerFile | Out-Null
}

$ProjectRoot = (Resolve-Path $ProjectRoot).Path
if (-not $ArchiveRoot) {
    $ArchiveRoot = Join-Path $ProjectRoot "archives"
}
$envFile = Read-DotEnv (Join-Path $ProjectRoot ".env")
$composeFile = Join-Path $ProjectRoot "docker-compose.yml"
$compose = @("-f", $composeFile)
$dbUser = Resolve-Setting $envFile "POSTGRES_USER" "postgres"
$dbName = Resolve-Setting $envFile "POSTGRES_DB" "postgres"

$timestamp = Get-Date -Format "yyyyMMdd_HHmmss"
$archiveDir = Join-Path $ArchiveRoot $timestamp
$dbDir = Join-Path $archiveDir "db"
$metaDir = Join-Path $archiveDir "meta"
New-Item -ItemType Directory -Force -Path $dbDir, $metaDir | Out-Null

$auditCutoffSql = "now() - interval '$AuditLogKeepDays days'"
$orderCutoffSql = "now() - interval '$OrderCandidateDays days'"
$auditCount = [int](Invoke-PsqlText -Sql "select count(*) from audit_log where created_at < $auditCutoffSql;" -ComposeArgs $compose -DbUser $dbUser -DbName $dbName)
$orderCandidateCount = [int](Invoke-PsqlText -Sql "select count(*) from order_rfq where created_at < $orderCutoffSql;" -ComposeArgs $compose -DbUser $dbUser -DbName $dbName)

$auditCsv = Join-Path $dbDir "audit_log_before_${AuditLogKeepDays}_days.csv"
$ordersCsv = Join-Path $dbDir "old_order_candidates_before_${OrderCandidateDays}_days.csv"
Export-QueryCsv `
    -Sql "select id, username, role, action, target_type, target_id, before_data, after_data, created_at from audit_log where created_at < $auditCutoffSql order by id" `
    -ContainerFile "/tmp/quote-archive-audit-log-$timestamp.csv" `
    -HostFile $auditCsv `
    -ComposeArgs $compose `
    -DbUser $dbUser `
    -DbName $dbName

Export-QueryCsv `
    -Sql "select id, rfq_no, customer_name, project_name, status, created_at from order_rfq where created_at < $orderCutoffSql order by created_at, id" `
    -ContainerFile "/tmp/quote-archive-old-orders-$timestamp.csv" `
    -HostFile $ordersCsv `
    -ComposeArgs $compose `
    -DbUser $dbUser `
    -DbName $dbName

$deletedAuditRows = 0
if (-not $DryRun -and $auditCount -gt 0) {
    $deletedAuditRows = [int](Invoke-PsqlText -Sql "with deleted as (delete from audit_log where created_at < $auditCutoffSql returning 1) select count(*) from deleted;" -ComposeArgs $compose -DbUser $dbUser -DbName $dbName)
    Invoke-Psql -Sql "vacuum analyze audit_log;" -ComposeArgs $compose -DbUser $dbUser -DbName $dbName
}

$meta = [ordered]@{
    archiveTime = (Get-Date).ToString("o")
    projectRoot = $ProjectRoot
    database = $dbName
    databaseUser = $dbUser
    dryRun = [bool]$DryRun
    auditLogKeepDays = $AuditLogKeepDays
    auditRowsExported = $auditCount
    auditRowsDeleted = $deletedAuditRows
    orderCandidateDays = $OrderCandidateDays
    orderCandidateRowsExported = $orderCandidateCount
    auditCsv = "db/" + (Split-Path $auditCsv -Leaf)
    oldOrderCandidatesCsv = "db/" + (Split-Path $ordersCsv -Leaf)
}
$meta | ConvertTo-Json -Depth 5 | Set-Content -Encoding utf8 (Join-Path $metaDir "archive.json")

Write-Host "Monthly archive completed: $archiveDir"
Write-Host "Audit rows exported: $auditCount"
Write-Host "Audit rows deleted: $deletedAuditRows"
Write-Host "Old order candidates exported only: $orderCandidateCount"
