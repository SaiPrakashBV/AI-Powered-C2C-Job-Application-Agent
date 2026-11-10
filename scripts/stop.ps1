<#
.SYNOPSIS  Stops the C2C Agent. Use -IncludeDatabase to also stop the docker PostgreSQL container.
.NOTES     Interrupted background jobs are safely re-queued on next start (durable job queue).
#>
param([switch]$IncludeDatabase)
. "$PSScriptRoot\common.ps1"
Import-DotEnv

$proc = Get-AppProcess
if ($proc) {
    Write-Step "Stopping application (PID $($proc.Id))"
    Stop-Process -Id $proc.Id
    $proc.WaitForExit(30000) | Out-Null
    Write-Ok "Stopped"
} else { Write-Ok "Application is not running" }
Remove-Item $PidFile -ErrorAction SilentlyContinue

if ($IncludeDatabase -and $env:C2C_DB_MODE -eq 'docker') {
    Write-Step "Stopping PostgreSQL container (data is kept in the docker volume)"
    Push-Location $RepoRoot
    try { docker compose --env-file $EnvFile stop postgres | Out-Host } finally { Pop-Location }
}
