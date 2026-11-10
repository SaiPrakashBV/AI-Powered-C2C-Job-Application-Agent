<# .SYNOPSIS  Development: run the Spring Boot backend from source (http://localhost:8080). #>
. "$PSScriptRoot\common.ps1"
Import-DotEnv
Push-Location (Join-Path $RepoRoot 'backend')
try { .\mvnw.cmd spring-boot:run } finally { Pop-Location }
