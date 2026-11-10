# Shared helpers for the C2C Agent PowerShell scripts. Dot-source: . "$PSScriptRoot\common.ps1"
$ErrorActionPreference = 'Stop'
$script:RepoRoot = Split-Path -Parent $PSScriptRoot
$script:RunDir   = Join-Path $RepoRoot 'run'
$script:JarPath  = Join-Path $RunDir 'c2c-agent.jar'
$script:PidFile  = Join-Path $RunDir 'app.pid'
$script:EnvFile  = Join-Path $RepoRoot '.env'

function Write-Step($msg) { Write-Host "==> $msg" -ForegroundColor Cyan }
function Write-Ok($msg)   { Write-Host "  [ok] $msg" -ForegroundColor Green }
function Write-Warn2($msg){ Write-Host "  [!] $msg" -ForegroundColor Yellow }
function Fail($msg)       { Write-Host "  [x] $msg" -ForegroundColor Red; exit 1 }

# Loads KEY=VALUE lines from .env into the current process environment (does not override existing vars).
function Import-DotEnv {
    if (-not (Test-Path $EnvFile)) { Fail ".env not found. Run scripts\setup.ps1 first." }
    foreach ($line in Get-Content $EnvFile) {
        $t = $line.Trim()
        if ($t -eq '' -or $t.StartsWith('#') -or -not $t.Contains('=')) { continue }
        $k, $v = $t.Split('=', 2)
        if (-not [Environment]::GetEnvironmentVariable($k.Trim(), 'Process')) {
            [Environment]::SetEnvironmentVariable($k.Trim(), $v.Trim(), 'Process')
        }
    }
}

function Get-AppPort { if ($env:C2C_PORT) { [int]$env:C2C_PORT } else { 8080 } }
function Get-DbPort  { if ($env:C2C_DB_PORT) { [int]$env:C2C_DB_PORT } else { 5432 } }

function Test-TcpPort([string]$HostName, [int]$Port) {
    try {
        $c = New-Object System.Net.Sockets.TcpClient
        $iar = $c.BeginConnect($HostName, $Port, $null, $null)
        $ok = $iar.AsyncWaitHandle.WaitOne(1000) -and $c.Connected
        $c.Close(); return $ok
    } catch { return $false }
}

function Test-AppHealth {
    try {
        $r = Invoke-RestMethod -Uri "http://127.0.0.1:$(Get-AppPort)/actuator/health" -TimeoutSec 3
        return $r.status -eq 'UP'
    } catch { return $false }
}

function Get-AppProcess {
    if (-not (Test-Path $PidFile)) { return $null }
    $appPid = Get-Content $PidFile -ErrorAction SilentlyContinue
    if (-not $appPid) { return $null }
    return Get-Process -Id $appPid -ErrorAction SilentlyContinue
}

function Test-JavaVersion {
    $java = Get-Command java -ErrorAction SilentlyContinue
    if (-not $java) { return $false }
    $out = & java -version 2>&1 | Out-String
    return $out -match 'version "(2[1-9]|[3-9]\d)'
}

function Start-Database {
    if ($env:C2C_DB_MODE -eq 'docker') {
        if (-not (Get-Command docker -ErrorAction SilentlyContinue)) { Fail "Docker is not installed. Install Docker Desktop or set C2C_DB_MODE=manual in .env." }
        Write-Step "Starting PostgreSQL (docker compose)"
        Push-Location $RepoRoot
        try { docker compose --env-file $EnvFile up -d postgres | Out-Host } finally { Pop-Location }
    }
    Write-Step "Waiting for PostgreSQL on localhost:$(Get-DbPort)"
    for ($i = 0; $i -lt 60; $i++) {
        if (Test-TcpPort 'localhost' (Get-DbPort)) { Write-Ok "PostgreSQL is reachable"; return }
        Start-Sleep -Seconds 1
    }
    Fail "PostgreSQL is not reachable on port $(Get-DbPort). Start it (Docker Desktop running?) and retry. See TROUBLESHOOTING in WINDOWS_SETUP.md."
}
