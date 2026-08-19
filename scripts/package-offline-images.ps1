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

function Ensure-DockerImageAvailable([string]$Image) {
    if ($SkipDependencyPull) {
        if (-not (Test-DockerImageExists $Image)) {
            throw "Docker image is required but not available locally: $Image"
        }
        Write-Host "Using local dependency image: $Image"
        return
    }

    Write-Host "+ docker pull $Image"
    & docker pull $Image
    if ($LASTEXITCODE -eq 0) {
        return
    }

    if (Test-DockerImageExists $Image) {
        Write-Warning "Failed to pull $Image, using the local copy instead."
        return
    }

    throw "Failed to pull required Docker image and no local copy exists: $Image"
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

Copy-Item -LiteralPath (Join-Path $ProjectRoot "docker-compose.yml") -Destination (Join-Path $OutputDir "docker-compose.yml") -Force
Copy-Item -LiteralPath (Join-Path $ProjectRoot "erp.env.example") -Destination (Join-Path $OutputDir "erp.env.example") -Force
Copy-Item -LiteralPath (Join-Path $ProjectRoot "QPO部署说明.md") -Destination (Join-Path $OutputDir "QPO部署说明.md") -Force
Copy-Item -LiteralPath (Join-Path $ProjectRoot "scripts/load-offline-images.ps1") -Destination (Join-Path $scriptsOutputDir "load-offline-images.ps1") -Force
Copy-Item -LiteralPath (Join-Path $ProjectRoot "scripts/load-offline-images.sh") -Destination (Join-Path $scriptsOutputDir "load-offline-images.sh") -Force
Copy-Item -LiteralPath (Join-Path $ProjectRoot "scripts/backup-qpo.sh") -Destination (Join-Path $scriptsOutputDir "backup-qpo.sh") -Force
Copy-Item -LiteralPath (Join-Path $ProjectRoot "scripts/restore-qpo.sh") -Destination (Join-Path $scriptsOutputDir "restore-qpo.sh") -Force
Copy-Item -LiteralPath (Join-Path $ProjectRoot "QPO服务器备份说明.md") -Destination (Join-Path $OutputDir "QPO服务器备份说明.md") -Force
Copy-Item -LiteralPath (Join-Path $ProjectRoot "QPO服务器恢复说明.md") -Destination (Join-Path $OutputDir "QPO服务器恢复说明.md") -Force

@"
QPO_IMAGE_TAG=$ImageTag
QPO_BACKEND_IMAGE=$backendImage
QPO_FRONTEND_IMAGE=$frontendImage
QPO_IMAGE_ARCHIVE=$(Split-Path -Leaf $archivePath)
"@ | Set-Content -Encoding UTF8 -Path (Join-Path $OutputDir "qpo-image.env")

$manifest = [ordered]@{
    imageTag = $ImageTag
    createdAt = (Get-Date).ToUniversalTime().ToString("yyyy-MM-ddTHH:mm:ssZ")
    archive = (Split-Path -Leaf $archivePath)
    images = $images
    includeMinio = [bool]$IncludeMinio
}
$manifest | ConvertTo-Json -Depth 4 | Set-Content -Encoding UTF8 -Path (Join-Path $OutputDir "qpo-offline-package-manifest.json")

Write-Host "Offline image package created: $OutputDir"
Write-Host "Image archive: $archivePath"
