<# .SYNOPSIS  Development: Vite dev server with hot reload (http://localhost:5173, proxies /api to :8080). #>
. "$PSScriptRoot\common.ps1"
Push-Location (Join-Path $RepoRoot 'frontend')
try { if (-not (Test-Path node_modules)) { npm ci }; npm run dev } finally { Pop-Location }
