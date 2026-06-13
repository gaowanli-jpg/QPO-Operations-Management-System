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

$ProjectRoot = (Resolve-Path $ProjectRoot).Path
if (-not $EnvFile) {
    $EnvFile = Join-Path $ProjectRoot "aps.env"
}
$EnvFile = (Resolve-Path $EnvFile).Path

$dbDump = Join-Path $BackupDir "db\aps.sql"
$metaFile = Join-Path $BackupDir "meta\backup.json"
if (-not (Test-Path -LiteralPath $dbDump)) {
    throw "APS database backup file not found: $dbDump"
}

$envValues = Read-DotEnv $EnvFile
$dbUser = Resolve-Setting $envValues "APS_POSTGRES_USER" "aps"
$dbName = Resolve-Setting $envValues "APS_POSTGRES_DB" "aps"
$dbPassword = Resolve-Setting $envValues "APS_POSTGRES_PASSWORD" "aps"
if (Test-Path -LiteralPath $metaFile) {
    $meta = Get-Content -LiteralPath $metaFile -Raw | ConvertFrom-Json
    if ($meta.databaseUser) {
        $dbUser = $meta.databaseUser
    }
    if ($meta.database) {
        $dbName = $meta.database
    }
}
if (-not $ContainerName) {
    $ContainerName = "aps-restore-drill-" + (Get-Date -Format "yyyyMMddHHmmss")
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
        throw "Temporary APS PostgreSQL container is not ready."
    }

    $containerDump = "/tmp/aps-restore-drill.sql"
    docker cp $dbDump "${ContainerName}:$containerDump" | Out-Null
    if ($LASTEXITCODE -ne 0) {
        throw "Failed to copy APS backup into temporary PostgreSQL container."
    }

    docker exec $ContainerName psql -v ON_ERROR_STOP=1 -U $dbUser -d $dbName -f $containerDump
    if ($LASTEXITCODE -ne 0) {
        throw "APS restore drill failed while loading dump."
    }

    $checkSql = "select 'aps_user' as table_name, count(*) from aps_user union all select 'aps_work_item', count(*) from aps_work_item union all select 'aps_department', count(*) from aps_department union all select 'aps_process', count(*) from aps_process union all select 'aps_material_inventory', count(*) from aps_material_inventory order by table_name;"
    docker exec $ContainerName psql -U $dbUser -d $dbName -c $checkSql
    if ($LASTEXITCODE -ne 0) {
        throw "APS restore drill verification query failed."
    }

    Write-Host "APS restore drill completed with temporary container: $ContainerName"
}
finally {
    docker rm -f $ContainerName 2>$null | Out-Null
}
