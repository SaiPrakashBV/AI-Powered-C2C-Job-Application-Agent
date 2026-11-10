<#
.SYNOPSIS  One-time installation: checks prerequisites, creates .env, starts PostgreSQL, builds the app.
.EXAMPLE   powershell -ExecutionPolicy Bypass -File scripts\setup.ps1
#>
param([switch]$SkipBuild)
. "$PSScriptRoot\common.ps1"

Write-Step "Checking prerequisites"
if (Test-JavaVersion) { Write-Ok "Java 21+ found" } else { Fail "Java 21 or newer is required (e.g. Eclipse Temurin 21). Install it and reopen PowerShell." }
if (-not $SkipBuild) {
    if (Get-Command node -ErrorAction SilentlyContinue) { Write-Ok "Node.js $(node -v) found (needed only to build)" }
    else { Fail "Node.js 20+ is required to build the frontend (not needed at runtime)." }
}

if (-not (Test-Path $EnvFile)) {
    Write-Step "Creating .env with a random database password"
    $bytes = New-Object byte[] 24
    [System.Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($bytes)
    $pw = [Convert]::ToBase64String($bytes).Replace('+','A').Replace('/','B').Replace('=','')
    (Get-Content (Join-Path $RepoRoot '.env.example')) -replace '^C2C_DB_PASSWORD=.*', "C2C_DB_PASSWORD=$pw" |
        Set-Content -Encoding ASCII $EnvFile
    Write-Ok ".env created (keep it private; it is git-ignored)"
} else { Write-Ok ".env already exists (left unchanged)" }

Import-DotEnv
if ($env:C2C_DB_MODE -eq 'manual') {
    Write-Warn2 "C2C_DB_MODE=manual: create database 'c2c_agent' and user '$($env:C2C_DB_USER)' yourself (see WINDOWS_SETUP.md)."
}
Start-Database

if (-not $SkipBuild) {
    Write-Step "Building frontend"
    Push-Location (Join-Path $RepoRoot 'frontend')
    try { npm ci; if ($LASTEXITCODE) { Fail "npm ci failed" }; npm run build; if ($LASTEXITCODE) { Fail "Frontend build failed" } } finally { Pop-Location }
    Write-Step "Building backend (bundles the frontend)"
    Push-Location (Join-Path $RepoRoot 'backend')
    try { .\mvnw.cmd -q -B package -DskipTests; if ($LASTEXITCODE) { Fail "Backend build failed" } } finally { Pop-Location }
    New-Item -ItemType Directory -Force $RunDir | Out-Null
    Copy-Item (Join-Path $RepoRoot 'backend\target\c2c-agent.jar') $JarPath -Force
    Write-Ok "Application built: $JarPath"
}

Write-Host ""
Write-Host "Setup complete. Launch with Start-C2CAgent.cmd (double-click) or scripts\start.ps1" -ForegroundColor Green
