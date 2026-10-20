param(
    [Parameter(Mandatory = $true, Position = 0)]
    [string]$Name,
    [switch]$DryRun
)

$ErrorActionPreference = "Stop"

$generator = Join-Path $PSScriptRoot "scripts/new-flyway-migration.ps1"
$arguments = @(
    "-NoProfile",
    "-ExecutionPolicy", "Bypass",
    "-File", $generator,
    "-Name", $Name,
    "-ProjectRoot", $PSScriptRoot
)

if ($DryRun) {
    $arguments += "-DryRun"
}

& powershell @arguments
exit $LASTEXITCODE
