param(
    [switch]$NoStop
)

$ErrorActionPreference = "Stop"

$RootDir = $PSScriptRoot
$ApsDir = Join-Path $RootDir "aps-system"
$EnvFile = Join-Path $RootDir ".env"

function Read-DotEnv {
    param([string]$Path)

    $values = @{}
    if (-not (Test-Path -LiteralPath $Path)) {
        return $values
    }

    foreach ($line in Get-Content -LiteralPath $Path) {
        $text = $line.Trim()
        if ($text.Length -eq 0 -or $text.StartsWith("#")) {
            continue
        }
        $index = $text.IndexOf("=")
        if ($index -lt 1) {
            continue
        }

        $key = $text.Substring(0, $index).Trim()
        $value = $text.Substring($index + 1).Trim()
        if (($value.StartsWith('"') -and $value.EndsWith('"')) -or ($value.StartsWith("'") -and $value.EndsWith("'"))) {
            $value = $value.Substring(1, $value.Length - 2)
        }
        $values[$key] = $value
    }
    return $values
}

function Get-ConfigValue {
    param(
        [hashtable]$Values,
        [string]$Name,
        [string]$Fallback
    )

    if ($Values.ContainsKey($Name) -and -not [string]::IsNullOrWhiteSpace($Values[$Name])) {
        return $Values[$Name]
    }
    return $Fallback
}

$config = Read-DotEnv -Path $EnvFile
$token = Get-ConfigValue -Values $config -Name "APS_ERP_AUTH_TOKEN" -Fallback ""
if ([string]::IsNullOrWhiteSpace($token)) {
    $token = Get-ConfigValue -Values $config -Name "ERP_APS_SOURCE_TOKEN" -Fallback ""
}
if ([string]::IsNullOrWhiteSpace($token)) {
    throw "Missing APS_ERP_AUTH_TOKEN or ERP_APS_SOURCE_TOKEN in $EnvFile"
}

$env:APS_ERP_AUTH_TOKEN = $token
$env:APS_ERP_BASE_URL = Get-ConfigValue -Values $config -Name "APS_ERP_BASE_URL" -Fallback "http://localhost:8080"
$env:DB_URL = Get-ConfigValue -Values $config -Name "APS_DB_URL" -Fallback "jdbc:postgresql://localhost:5433/aps"
$env:DB_USERNAME = Get-ConfigValue -Values $config -Name "APS_DB_USERNAME" -Fallback "aps"
$env:DB_PASSWORD = Get-ConfigValue -Values $config -Name "APS_DB_PASSWORD" -Fallback "aps123456"

if (-not $NoStop) {
    Get-CimInstance Win32_Process -Filter "name='java.exe'" |
        Where-Object {
            $_.CommandLine -like "*com.machining.apssystem.ApsSystemApplication*" -or
            ($_.CommandLine -like "*spring-boot:run*" -and $_.CommandLine -like "*aps-system*")
        } |
        ForEach-Object {
            Write-Host "Stopping existing APS process $($_.ProcessId)"
            Stop-Process -Id $_.ProcessId -Force
        }
}

Write-Host "Starting APS backend..."
Write-Host "APS_ERP_BASE_URL=$env:APS_ERP_BASE_URL"
Write-Host "DB_URL=$env:DB_URL"
Set-Location -LiteralPath $ApsDir
& .\mvnw.cmd spring-boot:run
