param(
    [string]$ProjectRoot = "",
    [string]$EnvFile = "erp.env",
    [switch]$NoBuild
)

$ErrorActionPreference = "Stop"

if ([string]::IsNullOrWhiteSpace($ProjectRoot)) {
    $ProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
}

function Run-Checked($Command, [string[]]$Arguments) {
    & $Command @Arguments
    if ($LASTEXITCODE -ne 0) {
        exit $LASTEXITCODE
    }
}

function Wait-Healthy($ContainerName, $TimeoutSeconds) {
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        $health = (& docker inspect -f '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' $ContainerName 2>$null)
        if ($LASTEXITCODE -eq 0 -and ($health | Select-Object -First 1).Trim() -eq "healthy") {
            return
        }
        Start-Sleep -Seconds 2
    }
    Write-Error "$ContainerName did not become healthy within $TimeoutSeconds seconds"
    exit 1
}

$envPath = Join-Path $ProjectRoot $EnvFile
if (-not (Test-Path $envPath)) {
    Write-Error "Compose env file not found: $envPath"
    exit 1
}

Set-Location $ProjectRoot

Run-Checked "powershell" @("-NoProfile", "-ExecutionPolicy", "Bypass", "-File", (Join-Path $ProjectRoot "scripts/check-flyway-migrations.ps1"), "-SkipDatabase")

Run-Checked "docker" @("compose", "--env-file", $envPath, "up", "-d", "postgres")
Wait-Healthy "quote-postgres" 90

Run-Checked "powershell" @("-NoProfile", "-ExecutionPolicy", "Bypass", "-File", (Join-Path $ProjectRoot "scripts/check-flyway-migrations.ps1"))

$composeArgs = @("compose", "--env-file", $envPath, "up", "-d")
if (-not $NoBuild) {
    $composeArgs += "--build"
}
$composeArgs += @("backend", "frontend")
Run-Checked "docker" $composeArgs

Wait-Healthy "quote-backend" 120
Wait-Healthy "quote-frontend" 60

Run-Checked "docker" @("compose", "--env-file", $envPath, "ps")
