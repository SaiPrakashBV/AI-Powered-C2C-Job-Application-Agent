<# .SYNOPSIS  Shows application, database and health status. #>
. "$PSScriptRoot\common.ps1"
Import-DotEnv
$proc = Get-AppProcess
Write-Host ("Application process : " + $(if ($proc) { "running (PID $($proc.Id))" } else { "not running" }))
Write-Host ("Health endpoint     : " + $(if (Test-AppHealth) { "UP" } else { "DOWN / unreachable" }))
Write-Host ("PostgreSQL port $(Get-DbPort) : " + $(if (Test-TcpPort 'localhost' (Get-DbPort)) { "reachable" } else { "not reachable" }))
Write-Host ("Database mode       : $($env:C2C_DB_MODE)")
Write-Host ("Dashboard           : http://localhost:$(Get-AppPort)")
