<#
.SYNOPSIS
    Starts Docker (if needed), brings up the local infra stack, and launches every
    backend service with the local profile in its own window.

.DESCRIPTION
    Extend this as new services land (M1+): add one entry to the $Services array
    below. Nothing else needs to change.

    Each service launches in its own PowerShell window (via run-service.ps1) so you
    can watch its logs live and Ctrl+C it individually without affecting the others.

.PARAMETER SkipInfra
    Skip `docker compose up` - useful if the infra stack is already running.

.PARAMETER DockerDesktopPath
    Override the path to Docker Desktop.exe, if it's not at the default or this
    machine's known custom install location.

.EXAMPLE
    .\start-local.ps1
.EXAMPLE
    .\start-local.ps1 -SkipInfra
#>

param(
    [switch]$SkipInfra,
    [string]$DockerDesktopPath
)

# Deliberately NOT using $ErrorActionPreference = "Stop": PowerShell 5.1 wraps a
# native command's stderr lines into a terminating error under that preference,
# and harmless stderr noise (e.g. Docker's WSL2 "No blkio throttle..." warning)
# would abort this script even though the command itself succeeded. The explicit
# `throw` calls below are what should actually stop execution.
$ScriptDir  = $PSScriptRoot
$BackendDir = Split-Path -Parent (Split-Path -Parent $ScriptDir)   # windows/ -> scripts/ -> backend/

# ---------------------------------------------------------------------------
# Services to start locally. Add a new entry here as new services are added
# under backend/ - Path is relative to backend/, Port is its server.port.
# ---------------------------------------------------------------------------
$Services = @(
    @{ Name = "user-service"; Path = "user-service"; Port = 8081 }
    @{ Name = "gateway";      Path = "gateway";      Port = 8080 }
    # @{ Name = "auth-service";     Path = "auth-service";     Port = 8082 }  # M1
    # @{ Name = "group-service";    Path = "group-service";    Port = 8083 }  # M2
    # @{ Name = "location-service"; Path = "location-service"; Port = 8084 }  # M3/M4
    # @{ Name = "websocket-service";Path = "websocket-service";Port = 8085 }  # M4
    # @{ Name = "notification-service"; Path = "notification-service"; Port = 8086 } # M5
)

function Test-DockerRunning {
    docker info *> $null
    return ($LASTEXITCODE -eq 0)
}

function Wait-ForDocker {
    param([int]$TimeoutSeconds = 90)
    $elapsed = 0
    while (-not (Test-DockerRunning)) {
        if ($elapsed -ge $TimeoutSeconds) {
            throw "Docker did not become ready within $TimeoutSeconds seconds."
        }
        Write-Host "  waiting for Docker engine... (${elapsed}s)" -ForegroundColor Yellow
        Start-Sleep -Seconds 5
        $elapsed += 5
    }
}

function Test-PortInUse {
    param([int]$Port)
    $null -ne (Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue)
}

# ---------------------------------------------------------------------------
# 1. Docker
# ---------------------------------------------------------------------------
Write-Host "==> Checking Docker..." -ForegroundColor Cyan
if (Test-DockerRunning) {
    Write-Host "  Docker is already running." -ForegroundColor Green
} else {
    $candidatePaths = @(
        $DockerDesktopPath
        "D:\docker\docker-desktop\Docker Desktop.exe"
        "C:\Program Files\Docker\Docker\Docker Desktop.exe"
    ) | Where-Object { $_ }

    $exe = $candidatePaths | Where-Object { Test-Path -LiteralPath $_ } | Select-Object -First 1
    if (-not $exe) {
        throw "Docker Desktop not found. Pass -DockerDesktopPath '<path to Docker Desktop.exe>'."
    }

    Write-Host "  Starting Docker Desktop from '$exe'..." -ForegroundColor Yellow
    Start-Process -FilePath $exe
    Wait-ForDocker
    Write-Host "  Docker is ready." -ForegroundColor Green
}

# ---------------------------------------------------------------------------
# 2. Local infra (docker compose)
# ---------------------------------------------------------------------------
if (-not $SkipInfra) {
    Write-Host "`n==> Starting local infra (docker compose)..." -ForegroundColor Cyan
    Push-Location -LiteralPath $BackendDir
    try {
        docker compose up -d

        Write-Host "  Waiting for Postgres to become healthy..." -ForegroundColor Yellow
        $elapsed = 0
        while ($true) {
            $status = docker inspect --format='{{.State.Health.Status}}' tmb-postgres 2>$null
            if ($status -eq "healthy") { break }
            if ($elapsed -ge 60) { throw "Postgres did not become healthy within 60s. Check: docker compose logs postgres" }
            Start-Sleep -Seconds 3
            $elapsed += 3
        }
        Write-Host "  Infra is up." -ForegroundColor Green
    } finally {
        Pop-Location
    }
} else {
    Write-Host "`n==> Skipping infra startup (-SkipInfra)." -ForegroundColor DarkGray
}

# ---------------------------------------------------------------------------
# 3. Backend services - each in its own window via run-service.ps1
# ---------------------------------------------------------------------------
Write-Host "`n==> Starting backend services..." -ForegroundColor Cyan
$runServiceScript = Join-Path $ScriptDir "run-service.ps1"

foreach ($svc in $Services) {
    if (Test-PortInUse -Port $svc.Port) {
        Write-Host "  $($svc.Name): port $($svc.Port) already in use - skipping (already running?)." -ForegroundColor DarkYellow
        continue
    }

    $servicePath = Join-Path $BackendDir $svc.Path
    Write-Host "  Launching $($svc.Name) on port $($svc.Port)..." -ForegroundColor Yellow
    Start-Process powershell -ArgumentList @(
        "-NoExit", "-File", $runServiceScript,
        "-ServicePath", $servicePath,
        "-ServiceName", $svc.Name
    )
}

# ---------------------------------------------------------------------------
# 4. Wait for each service to report healthy
# ---------------------------------------------------------------------------
Write-Host "`n==> Waiting for services to come up (can take ~20-40s each on first run)..." -ForegroundColor Cyan
foreach ($svc in $Services) {
    $url = "http://localhost:$($svc.Port)/actuator/health"
    $elapsed = 0
    $ok = $false
    while ($elapsed -lt 90) {
        try {
            $resp = Invoke-RestMethod -Uri $url -TimeoutSec 3
            if ($resp.status -eq "UP") { $ok = $true; break }
        } catch { }
        Start-Sleep -Seconds 3
        $elapsed += 3
    }
    if ($ok) {
        Write-Host "  $($svc.Name): UP" -ForegroundColor Green
    } else {
        Write-Host "  $($svc.Name): not responding yet after ${elapsed}s - check its window for errors." -ForegroundColor Red
    }
}

Write-Host "`nAll done. Each service's window stays open with live logs; Ctrl+C in any of" -ForegroundColor Cyan
Write-Host "them stops just that service. Infra: docker compose ps | down (from backend/)." -ForegroundColor DarkGray
