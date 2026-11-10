<#
.SYNOPSIS  Runs backend (JUnit + Testcontainers) and frontend (Vitest) tests.
.NOTES     Backend integration tests need Docker (Testcontainers) OR a disposable database given by
           C2C_TEST_DB_URL / C2C_TEST_DB_USER / C2C_TEST_DB_PASSWORD (all tables in it are truncated!).
#>
param([switch]$BackendOnly, [switch]$FrontendOnly)
. "$PSScriptRoot\common.ps1"
$failed = $false
if (-not $FrontendOnly) {
    Write-Step "Backend tests"
    Push-Location (Join-Path $RepoRoot 'backend')
    try { .\mvnw.cmd -B test; if ($LASTEXITCODE) { $failed = $true } } finally { Pop-Location }
}
if (-not $BackendOnly) {
    Write-Step "Frontend typecheck + tests"
    Push-Location (Join-Path $RepoRoot 'frontend')
    try {
        if (-not (Test-Path node_modules)) { npm ci }
        npm run typecheck; if ($LASTEXITCODE) { $failed = $true }
        npm test; if ($LASTEXITCODE) { $failed = $true }
    } finally { Pop-Location }
}
if ($failed) { Fail "Some tests failed" } else { Write-Ok "All tests passed" }
