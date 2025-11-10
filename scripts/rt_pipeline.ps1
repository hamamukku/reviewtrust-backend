param(
    [string]$Profile = "dev",
    [string]$DbContainer = "reviewtrust_db",
    [string]$Email = "admin@example.com",
    [string]$Password,
    [string]$LabelsCsv = "$PSScriptRoot\..\data\labels.csv",
    [string]$ProductsCsv = "$PSScriptRoot\products_map.csv",
    [string]$ProductIdForProof,
    [int]$Port = 8080
)

function Invoke-PsqlCopy {
    param([string]$Command)
    $psql = Get-Command psql -ErrorAction SilentlyContinue
    if (-not $psql) {
        Write-Warning "psql not found; skipping SQL export"
        return
    }
    $url = $Env:SPRING_DATASOURCE_URL
    if (-not $url) { $url = "jdbc:postgresql://localhost:5432/reviewtrust" }
    if ($url -notmatch "jdbc:postgresql://([^:/]+)(?::(\d+))?/(.+)") {
        throw "Cannot parse SPRING_DATASOURCE_URL: $url"
    }
    $host = $matches[1]
    $portNum = if ($matches[2]) { $matches[2] } else { "5432" }
    $dbname = $matches[3]
    $user = $Env:SPRING_DATASOURCE_USERNAME
    if (-not $user) { $user = "app" }
    $pass = $Env:SPRING_DATASOURCE_PASSWORD
    if (-not $pass) { $pass = "app" }
    $env:PGPASSWORD = $pass
    & psql -h $host -p $portNum -U $user -d $dbname -c $Command | Out-Null
    Remove-Item Env:PGPASSWORD -ErrorAction SilentlyContinue
}

# 1) env + DB
& $PSScriptRoot\rt_env.ps1 -Profile $Profile -DbContainer $DbContainer

# 2) boot (blocking)
Write-Host "=== BOOT ==="
& $PSScriptRoot\rt_boot.ps1 -Profile $Profile -DbContainer $DbContainer -Port $Port -Debug:$false

# 3) login
Write-Host "=== LOGIN ==="
& $PSScriptRoot\rt_login.ps1 -Email $Email -Password $Password -BaseUrl "http://localhost:$Port"

# 4) seed products from labels
if (Test-Path $LabelsCsv) {
  Write-Host "=== SEED PRODUCTS ==="
  & $PSScriptRoot\rt_seed_products.ps1 -CsvPath $LabelsCsv -BaseUrl "http://localhost:$Port" -OutCsv $ProductsCsv
} else {
  Write-Warning "Labels CSV not found: $LabelsCsv (skip seed)"
}

# 5) batch rescrape using generated map if available
if (Test-Path $ProductsCsv) {
  Write-Host "=== RESCRAPE BATCH ==="
  & $PSScriptRoot\rt_rescrape_batch.ps1 -CsvPath $ProductsCsv -BaseUrl "http://localhost:$Port"
} else {
  Write-Warning "Products CSV not found: $ProductsCsv (skip rescrape batch)"
}

# 6) thresholds (idempotent)
Write-Host "=== THRESHOLDS ==="
& $PSScriptRoot\rt_thresholds_build.ps1 -Force

# 7) export features/confusion if possible
$proofDir = "$PSScriptRoot\..\delivery\proof"
New-Item -ItemType Directory -Path $proofDir -Force | Out-Null

$featuresSql = Get-Content "$PSScriptRoot\rt_features_sql.sql" -Raw
$featuresOut = Join-Path $proofDir "features.csv"
Invoke-PsqlCopy "\\copy ($featuresSql) TO '$featuresOut' CSV HEADER"

$confusionSqlRaw = Get-Content "$PSScriptRoot\rt_confusion_sql.sql" -Raw
$confusionParts = $confusionSqlRaw -split ";"
if ($confusionParts.Length -ge 1) {
  $confusionQuery = $confusionParts[0]
  $confusionOut = Join-Path $proofDir "confusion_matrix.csv"
  Invoke-PsqlCopy "\\copy ($confusionQuery) TO '$confusionOut' CSV HEADER"
}
if ($confusionParts.Length -ge 2) {
  $accuracyQuery = $confusionParts[1]
  if ($accuracyQuery.Trim()) {
    Invoke-PsqlCopy $accuracyQuery
  }
}

# 8) collect proof artifacts
if ($ProductIdForProof) {
  Write-Host "=== PROOF ==="
  & $PSScriptRoot\rt_collect_proof.ps1 -ProductId $ProductIdForProof -BaseUrl "http://localhost:$Port"
} else {
  Write-Host "Skip proof (no ProductIdForProof)"
}

Write-Host "=== PIPELINE DONE ==="
