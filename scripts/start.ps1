<#
.SYNOPSIS  Starts PostgreSQL (docker mode) and the C2C Agent, waits for health, opens the dashboard.
#>
param([switch]$NoBrowser)
. "$PSScriptRoot\common.ps1"
Import-DotEnv

$proc = Get-AppProcess
if ($proc -and (Test-AppHealth)) {
    Write-Ok "Already running (PID $($proc.Id))"
    if (-not $NoBrowser) { Start-Process "http://localhost:$(Get-AppPort)" }
    exit 0
}
if (-not (Test-Path $JarPath)) { Fail "Application not built. Run scripts\setup.ps1 first." }
if (-not (Test-JavaVersion)) { Fail "Java 21+ not found on PATH." }
if (Test-TcpPort '127.0.0.1' (Get-AppPort)) { Fail "Port $(Get-AppPort) is already in use by another program. Change C2C_PORT in .env." }

Start-Database

Write-Step "Starting application"
$logDir = if ($env:C2C_DATA_DIR) { Join-Path $env:C2C_DATA_DIR 'logs' } else { Join-Path $env:USERPROFILE 'C2CAgent\data\logs' }
New-Item -ItemType Directory -Force $logDir | Out-Null
$javaArgs = @()
if ($env:C2C_JAVA_OPTS) { $javaArgs += $env:C2C_JAVA_OPTS.Split(' ', [StringSplitOptions]::RemoveEmptyEntries) }
$javaArgs += @('-jar', "`"$JarPath`"")
$p = Start-Process -FilePath 'java' -ArgumentList $javaArgs -WorkingDirectory $RunDir -WindowStyle Hidden `
        -RedirectStandardOutput (Join-Path $logDir 'console.out.log') -RedirectStandardError (Join-Path $logDir 'console.err.log') -PassThru
Set-Content -Path $PidFile -Value $p.Id

Write-Step "Waiting for health check (up to 120 s)"
for ($i = 0; $i -lt 120; $i++) {
    if ($p.HasExited) { Fail "Application exited during startup. See $logDir\c2c-agent.log and console.err.log" }
    if (Test-AppHealth) {
        Write-Ok "C2C Agent is running at http://localhost:$(Get-AppPort) (PID $($p.Id))"
        if (-not $NoBrowser) { Start-Process "http://localhost:$(Get-AppPort)" }
        exit 0
    }
    Start-Sleep -Seconds 1
}
Fail "Health check timed out. See $logDir\c2c-agent.log"
