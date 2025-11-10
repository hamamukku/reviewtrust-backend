param(
    [string]$ProductId,
    [string]$BaseUrl = "http://localhost:8080",
    [string]$TokenPath = "$PSScriptRoot\..\token.jwt",
    [string]$OutDir = "$PSScriptRoot\..\delivery\proof",
    [switch]$Force
)
Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

if (-not $ProductId) { throw "-ProductId is required" }
if (-not (Test-Path $TokenPath)) { throw "Token not found: $TokenPath (run rt_login.ps1)" }
$token = Get-Content $TokenPath -Raw

$ts = Get-Date -Format "yyyyMMdd-HHmmss"
$dir = Join-Path $OutDir $ts
New-Item -ItemType Directory -Path $dir -Force | Out-Null

# API scores snapshot
try {
  $scores = Invoke-RestMethod -Method Get -Uri "$BaseUrl/api/products/$ProductId/scores"
  ($scores | ConvertTo-Json -Depth 6) | Set-Content -Path (Join-Path $dir "scores.json") -Encoding UTF8
  Write-Host "[OK] scores.json"
} catch {
  Write-Warning "scores fetch failed: $($_.Exception.Message)"
}

# OpenAPI snapshot
try {
  $openapi = Invoke-RestMethod -Method Get -Uri "$BaseUrl/v3/api-docs"
  ($openapi | ConvertTo-Json -Depth 100) | Set-Content -Path (Join-Path $dir "openapi.json") -Encoding UTF8
  Write-Host "[OK] openapi.json"
} catch {
  Write-Warning "openapi fetch failed: $($_.Exception.Message)"
}

# Last 10 commands (dev proof)
try {
  Get-History | Select-Object -Last 10 | Out-File -FilePath (Join-Path $dir "last-10-commands.txt") -Encoding UTF8
} catch {}

Write-Host "[DONE] proof -> $dir"
