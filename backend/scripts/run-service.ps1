<#
.SYNOPSIS
    Launches one backend service with ./gradlew.bat bootRun under a given Spring profile.

.DESCRIPTION
    Meant to be run in its own window (start-local.ps1 does this for every service in
    its list), but works standalone too - handy for restarting just one service:

        .\run-service.ps1 -ServicePath ..\user-service -ServiceName user-service

.PARAMETER ServicePath
    Path to the service's directory (the one containing gradlew.bat).

.PARAMETER ServiceName
    Used only for the window title.

.PARAMETER Profile
    Spring profile to activate. Defaults to "local".
#>

param(
    [Parameter(Mandatory)] [string]$ServicePath,
    [Parameter(Mandatory)] [string]$ServiceName,
    [string]$Profile = "local"
)

$Host.UI.RawUI.WindowTitle = "$ServiceName ($Profile)"
Set-Location -LiteralPath $ServicePath
$env:SPRING_PROFILES_ACTIVE = $Profile

Write-Host "=== $ServiceName - profile '$Profile' ===" -ForegroundColor Cyan
& ".\gradlew.bat" bootRun
