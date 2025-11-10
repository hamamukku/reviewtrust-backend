param(
    [string]$Profile = "dev",
    [string]$DbContainer = "reviewtrust_db",
    [string]$Email = "admin@example.com",
    [string]$Password,
    [string]$ProductsCsv = "$PSScriptRoot\products.csv",
    [string]$ProductIdForProof,
    [int]$Port = 8080
)
# 1) env + DB
& $PSScriptRoot\rt_env.ps1 -Profile $Profile -DbContainer $DbContainer

# 2) boot (non-blocking? here: blocking; run in another terminal for full async)
Write-Host "=== BOOT ==="
& $PSScriptRoot\rt_boot.ps1 -Profile $Profile -DbContainer $DbContainer -Port $Port -Debug:$false
# If you want separated terminals, comment the above and run manually in a new shell.

# 3) login
Write-Host "=== LOGIN ==="
& $PSScriptRoot\rt_login.ps1 -Email $Email -Password $Password -BaseUrl "http://localhost:$Port"

# 4) batch rescrape
if (Test-Path $ProductsCsv) {
  Write-Host "=== RESCRAPE BATCH ==="
  & $PSScriptRoot\rt_rescrape_batch.ps1 -CsvPath $ProductsCsv -BaseUrl "http://localhost:$Port"
} else {
  Write-Warning "Products CSV not found: $ProductsCsv (skip rescrape batch)"
}

# 5) thresholds (idempotent)
Write-Host "=== THRESHOLDS ==="
& $PSScriptRoot\rt_thresholds_build.ps1 -Force

# 6) collect proof
if ($ProductIdForProof) {
  Write-Host "=== PROOF ==="
  & $PSScriptRoot\rt_collect_proof.ps1 -ProductId $ProductIdForProof -BaseUrl "http://localhost:$Port"
} else {
  Write-Host "Skip proof (no ProductIdForProof)"
}

Write-Host "=== PIPELINE DONE ==="
