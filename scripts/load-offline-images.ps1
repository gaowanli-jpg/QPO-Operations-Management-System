param(
    [string]$ProjectRoot = "",
    [string]$EnvFile = "erp.env",
    [string]$ImageArchive = "",
    [string]$ImageTag = "",
    [switch]$IncludeMinio,
    [switch]$NoStart
)

$ErrorActionPreference = "Stop"

if ([string]::IsNullOrWhiteSpace($ProjectRoot)) {
    $ProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
}

function Run-Checked($Command, [string[]]$Arguments) {
    Write-Host "+ $Command $($Arguments -join ' ')"
    & $Command @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "Command failed with exit code $LASTEXITCODE`: $Command $($Arguments -join ' ')"
    }
}

function Read-PackagedImageTag([string]$Root) {
    $imageEnvPath = Join-Path $Root "qpo-image.env"
    if (Test-Path $imageEnvPath) {
        foreach ($line in Get-Content -Path $imageEnvPath) {
            if ($line -match '^QPO_IMAGE_TAG=(.+)$') {
                return $Matches[1].Trim()
            }
        }
    }
    return ""
}

Set-Location $ProjectRoot

if ([string]::IsNullOrWhiteSpace($ImageArchive)) {
    $archive = Get-ChildItem -Path $ProjectRoot -Filter "qpo-offline-images-*.tar" | Sort-Object LastWriteTime -Descending | Select-Object -First 1
    if ($null -eq $archive) {
        throw "No qpo-offline-images-*.tar archive found in $ProjectRoot"
    }
    $ImageArchive = $archive.FullName
} else {
    $ImageArchive = $ExecutionContext.SessionState.Path.GetUnresolvedProviderPathFromPSPath($ImageArchive)
}

Run-Checked "docker" @("load", "-i", $ImageArchive)

if ([string]::IsNullOrWhiteSpace($ImageTag)) {
    $ImageTag = Read-PackagedImageTag $ProjectRoot
}
if ([string]::IsNullOrWhiteSpace($ImageTag) -and (Split-Path -Leaf $ImageArchive) -match '^qpo-offline-images-(.+)\.tar$') {
    $ImageTag = $Matches[1]
}
if ([string]::IsNullOrWhiteSpace($ImageTag)) {
    $ImageTag = "latest"
}
$env:QPO_IMAGE_TAG = $ImageTag

if ($NoStart) {
    Write-Host "Images loaded. Skipped compose startup because -NoStart was set."
    exit 0
}

$envPath = Join-Path $ProjectRoot $EnvFile
if (-not (Test-Path $envPath)) {
    $examplePath = Join-Path $ProjectRoot "erp.env.example"
    if (Test-Path $examplePath) {
        Copy-Item -LiteralPath $examplePath -Destination $envPath -Force
        throw "Created $envPath from erp.env.example. Fill required secrets, then rerun this script."
    }
    throw "Compose env file not found: $envPath"
}

$composeArgs = @("compose", "--env-file", $envPath)
if ($IncludeMinio) {
    $composeArgs += @("--profile", "minio")
}
$composeArgs += @("up", "-d", "--no-build")
Run-Checked "docker" $composeArgs
Run-Checked "docker" @("compose", "--env-file", $envPath, "ps")
