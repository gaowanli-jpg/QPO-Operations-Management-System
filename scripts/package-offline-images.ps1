param(
    [string]$ProjectRoot = "",
    [string]$OutputDir = "",
    [string]$ImageTag = "",
    [switch]$IncludeMinio,
    [switch]$SkipDependencyPull
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

function Test-DockerImageExists([string]$Image) {
    & docker image inspect $Image --format '{{.Id}}' *> $null
    return $LASTEXITCODE -eq 0
}

function Get-DockerImageShortId([string]$Image) {
    $id = & docker image inspect $Image --format '{{.Id}}' 2>$null
    if ([string]::IsNullOrWhiteSpace($id)) {
        return "-"
    }
    $id = $id.Trim() -replace '^sha256:', ''
    if ($id.Length -gt 12) {
        return $id.Substring(0, 12)
    }
    return $id
}

function Ensure-DockerImageAvailable([string]$Image) {
    # Local first: offline build hosts cannot reach Docker Hub. Reuse an existing
    # local copy instead of waiting for a pull timeout on every run.
    if (Test-DockerImageExists $Image) {
        Write-Host "Using local image: $Image ($(Get-DockerImageShortId $Image))"
        return
    }

    if ($SkipDependencyPull) {
        throw "Docker image is required but not available locally: $Image"
    }

    Write-Host "+ docker pull $Image"
    & docker pull $Image
    if ($LASTEXITCODE -ne 0) {
        throw "Failed to pull required Docker image and no local copy exists: $Image"
    }
}

function Sanitize-ImageTag([string]$Tag) {
    $candidate = $Tag
    if ([string]::IsNullOrWhiteSpace($candidate)) {
        $candidate = "latest"
    }
    $candidate = $candidate.Trim() -replace '[^A-Za-z0-9_.-]', '-'
    $candidate = $candidate.Trim('.', '-')
    if ([string]::IsNullOrWhiteSpace($candidate)) {
        $candidate = "latest"
    }
    if ($candidate.Length -gt 120) {
        $candidate = $candidate.Substring(0, 120)
    }
    return $candidate
}

function Resolve-DefaultImageTag() {
    $exactTag = (& git -C $ProjectRoot describe --tags --exact-match HEAD 2>$null)
    if ($LASTEXITCODE -eq 0 -and -not [string]::IsNullOrWhiteSpace($exactTag)) {
        return $exactTag.Trim()
    }

    $branch = (& git -C $ProjectRoot rev-parse --abbrev-ref HEAD 2>$null)
    if ($LASTEXITCODE -eq 0 -and -not [string]::IsNullOrWhiteSpace($branch)) {
        $branch = $branch.Trim()
        if ($branch -eq "master" -or $branch -eq "main") {
            return $branch
        }
    }

    $shortSha = (& git -C $ProjectRoot rev-parse --short=12 HEAD 2>$null)
    if ($LASTEXITCODE -eq 0 -and -not [string]::IsNullOrWhiteSpace($shortSha)) {
        return $shortSha.Trim()
    }

    return "local"
}

function Copy-PackageFile([string]$RelativePath, [string]$DestinationDirectory) {
    $source = Join-Path $ProjectRoot $RelativePath
    if (-not (Test-Path -LiteralPath $source)) {
        throw "Required package file does not exist: $source"
    }
    Copy-Item -LiteralPath $source -Destination (Join-Path $DestinationDirectory (Split-Path -Leaf $RelativePath)) -Force
}

function Write-Utf8NoBom([string]$Path, [string]$Content) {
    # Set-Content -Encoding UTF8 on Windows PowerShell 5.1 prepends a BOM.
    # A BOM breaks the Linux side: grep '^QPO_IMAGE_TAG=' misses the first line
    # and `. qpo-image.env` fails with "command not found".
    $utf8NoBom = New-Object System.Text.UTF8Encoding($false)
    [System.IO.File]::WriteAllText($Path, $Content, $utf8NoBom)
}

$ImageTag = Sanitize-ImageTag $(if ([string]::IsNullOrWhiteSpace($ImageTag)) { Resolve-DefaultImageTag } else { $ImageTag })

if ([string]::IsNullOrWhiteSpace($OutputDir)) {
    $OutputDir = Join-Path $ProjectRoot (Join-Path "dist" "qpo-offline-package-$ImageTag")
}

$OutputDir = $ExecutionContext.SessionState.Path.GetUnresolvedProviderPathFromPSPath($OutputDir)
$scriptsOutputDir = Join-Path $OutputDir "scripts"
New-Item -ItemType Directory -Force -Path $scriptsOutputDir | Out-Null

$backendImage = "qpo-backend:$ImageTag"
$frontendImage = "qpo-frontend:$ImageTag"
$postgresImage = "postgres:15"
$minioImage = "minio/minio:latest"

Set-Location $ProjectRoot
Run-Checked "docker" @("version")

Run-Checked "docker" @("build", "-t", $backendImage, (Join-Path $ProjectRoot "quote-backend"))
Run-Checked "docker" @("build", "-t", $frontendImage, (Join-Path $ProjectRoot "quote-frontend"))

Ensure-DockerImageAvailable $postgresImage
if ($IncludeMinio) {
    Ensure-DockerImageAvailable $minioImage
}

$images = @($backendImage, $frontendImage, $postgresImage)
if ($IncludeMinio) {
    $images += $minioImage
}

$archivePath = Join-Path $OutputDir "qpo-offline-images-$ImageTag.tar"
if (Test-Path $archivePath) {
    Remove-Item -LiteralPath $archivePath -Force
}
Run-Checked "docker" (@("save", "-o", $archivePath) + $images)

Copy-PackageFile "docker-compose.yml" $OutputDir
Copy-PackageFile "erp.env.example" $OutputDir
Get-ChildItem -LiteralPath $ProjectRoot -Filter "QPO*.md" -File |
    Copy-Item -Destination $OutputDir -Force
Copy-PackageFile "scripts/load-offline-images.ps1" $scriptsOutputDir
Copy-PackageFile "scripts/load-offline-images.sh" $scriptsOutputDir
Copy-PackageFile "scripts/backup-qpo.sh" $scriptsOutputDir
Copy-PackageFile "scripts/restore-qpo.sh" $scriptsOutputDir

$imageEnvLines = @(
    "QPO_IMAGE_TAG=$ImageTag"
    "QPO_BACKEND_IMAGE=$backendImage"
    "QPO_FRONTEND_IMAGE=$frontendImage"
    "QPO_IMAGE_ARCHIVE=$(Split-Path -Leaf $archivePath)"
)
Write-Utf8NoBom (Join-Path $OutputDir "qpo-image.env") (($imageEnvLines -join "`n") + "`n")

$manifest = [ordered]@{
    imageTag = $ImageTag
    createdAt = (Get-Date).ToUniversalTime().ToString("yyyy-MM-ddTHH:mm:ssZ")
    archive = (Split-Path -Leaf $archivePath)
    images = $images
    includeMinio = [bool]$IncludeMinio
}
Write-Utf8NoBom (Join-Path $OutputDir "qpo-offline-package-manifest.json") (($manifest | ConvertTo-Json -Depth 4) + "`n")

Write-Host "Offline image package created: $OutputDir"
Write-Host "Image archive: $archivePath"
