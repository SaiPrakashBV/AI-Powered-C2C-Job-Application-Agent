<# .SYNOPSIS  Development: start only PostgreSQL. #>
. "$PSScriptRoot\common.ps1"
Import-DotEnv
Start-Database
