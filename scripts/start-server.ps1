# Starts the local Minecraft 26.1 server for mc-build-crew.
# Usage (from the project folder):  powershell -ExecutionPolicy Bypass -File scripts\start-server.ps1
# Stop it by typing  stop  in this window (saves the world cleanly).

$ErrorActionPreference = 'Stop'
$serverDir = Join-Path $PSScriptRoot '..\server' | Resolve-Path

# Find java.exe: PATH first, then the Temurin install folder, since PATH only
# refreshes in new windows right after a winget install.
$java = (Get-Command java -ErrorAction SilentlyContinue).Source
if (-not $java) {
    $java = Get-ChildItem 'C:\Program Files\Eclipse Adoptium\jre-25*\bin\java.exe', 'C:\Program Files\Eclipse Adoptium\jdk-25*\bin\java.exe' -ErrorAction SilentlyContinue |
        Select-Object -First 1 -ExpandProperty FullName
}
if (-not $java) { throw 'Java 25 not found. Install it with: winget install --id EclipseAdoptium.Temurin.25.JRE -e' }

# Minecraft 26.1 needs Java 25 or newer; fail early with a clear message instead of a stack trace.
$verLine = (& cmd /c "`"$java`" -version 2>&1") | Select-Object -First 1
if ($verLine -notmatch 'version "(\d+)') { throw "Could not read Java version: $verLine" }
if ([int]$Matches[1] -lt 25) { throw "Java $($Matches[1]) found at $java, but Minecraft 26.1 needs Java 25+." }

# Safety: refuse to start if someone changed the server to listen beyond this laptop.
$props = Get-Content (Join-Path $serverDir 'server.properties') -ErrorAction SilentlyContinue
if (-not ($props -match '^server-ip=127\.0\.0\.1$')) {
    throw 'server.properties must contain server-ip=127.0.0.1 (the server runs in offline mode, so it must stay local).'
}

$eula = Join-Path $serverDir 'eula.txt'
if (-not (Test-Path $eula) -or -not (Select-String -Path $eula -Pattern '^eula=true' -Quiet)) {
    Write-Host 'You need to accept the Minecraft EULA first: https://aka.ms/MinecraftEULA' -ForegroundColor Yellow
    Write-Host "Then open $eula in Notepad and change eula=false to eula=true." -ForegroundColor Yellow
}

Write-Host "Starting Minecraft server with $java (Ctrl+C or type 'stop' to quit)"
Push-Location $serverDir
try {
    # Small heap for a weak laptop; raise -Xmx to 1536M only if the server lags.
    & $java -Xms512M -Xmx1G -jar server.jar nogui
} finally {
    Pop-Location
}
