param(
    [Parameter(Mandatory = $true)]
    [string]$BackupDir,
    [string]$ProjectRoot = "D:\Code",
    [string]$EnvFile = "",
    [string]$ContainerName = "",
    [string]$Image = "postgres:15"
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

$ProjectRoot = (Resolve-Path $ProjectRoot).Path
if (-not $EnvFile) {
    $EnvFile = Join-Path $ProjectRoot "erp.env"
}
$EnvFile = (Resolve-Path $EnvFile).Path
$dbDump = Join-Path $BackupDir "db\postgres.sql"
$metaFile = Join-Path $BackupDir "meta\backup.json"
if (-not (Test-Path $dbDump)) {
    throw "Database backup file not found: $dbDump"
}

$envValues = Read-DotEnv $EnvFile
$dbUser = Resolve-Setting $envValues "POSTGRES_USER" "postgres"
$dbName = Resolve-Setting $envValues "POSTGRES_DB" "postgres"
$dbPassword = Resolve-Setting $envValues "POSTGRES_PASSWORD" "123456"
if (Test-Path $metaFile) {
    $meta = Get-Content $metaFile -Raw | ConvertFrom-Json
    if ($meta.databaseUser) {
        $dbUser = $meta.databaseUser
    }
    if ($meta.database) {
        $dbName = $meta.database
    }
}
if (-not $ContainerName) {
    $ContainerName = "quote-restore-drill-" + (Get-Date -Format "yyyyMMddHHmmss")
}

try {
    docker run -d --rm `
        --name $ContainerName `
        -e POSTGRES_USER=$dbUser `
        -e POSTGRES_PASSWORD=$dbPassword `
        -e POSTGRES_DB=$dbName `
        $Image | Out-Null

    $ready = $false
    for ($i = 0; $i -lt 30; $i++) {
        docker exec $ContainerName pg_isready -U $dbUser -d $dbName | Out-Null
        if ($LASTEXITCODE -eq 0) {
            $ready = $true
            break
        }
        Start-Sleep -Seconds 2
    }
    if (-not $ready) {
        throw "Temporary PostgreSQL container is not ready."
    }

    $containerDump = "/tmp/quote-backend-restore-drill.sql"
    docker cp $dbDump "${ContainerName}:$containerDump" | Out-Null
    if ($LASTEXITCODE -ne 0) {
        throw "Failed to copy backup into temporary PostgreSQL container."
    }

    docker exec $ContainerName psql -v ON_ERROR_STOP=1 -U $dbUser -d $dbName -f $containerDump
    if ($LASTEXITCODE -ne 0) {
        throw "Restore drill failed while loading dump."
    }

    $checkSql = "select 'order_rfq' as table_name, count(*) from order_rfq union all select 'item_quote_version', count(*) from item_quote_version union all select 'order_delivery_ticket', count(*) from order_delivery_ticket union all select 'order_process_sheet', count(*) from order_process_sheet union all select 'dict_material', count(*) from dict_material union all select 'dict_process', count(*) from dict_process union all select 'erp_user', count(*) from erp_user order by table_name;"
    docker exec $ContainerName psql -U $dbUser -d $dbName -c $checkSql
    if ($LASTEXITCODE -ne 0) {
        throw "Restore drill verification query failed."
    }

    Write-Host "Restore drill completed with temporary container: $ContainerName"
}
finally {
    docker rm -f $ContainerName 2>$null | Out-Null
}
