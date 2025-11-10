param(
    [string]$CsvPath = "$PSScriptRoot\products.csv",  # columns: id,asin,url,name
    [string]$BaseUrl = "http://localhost:8080",
    [string]$TokenPath = "$PSScriptRoot\..\token.jwt",
    [int]$Limit = 50,
    [string]$OutCsv = "$PSScriptRoot\rescrape_result.csv",
    [switch]$Force
)
Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

if (-not (Test-Path $TokenPath)) { throw "Token not found: $TokenPath (run rt_login.ps1)" }
$token = Get-Content $TokenPath -Raw

if (-not (Test-Path $CsvPath)) { throw "CSV not found: $CsvPath" }
$rows = Import-Csv $CsvPath

$results = @()
foreach ($r in $rows) {
  $pid = $r.id
  $url = $r.url
  if (-not $pid -and $r.asin) { $pid = $r.asin }
  if (-not $pid) { Write-Warning "skip row (no id/asin): $($r | ConvertTo-Json -Compress)"; continue }

  try {
    if (-not $url -and $r.asin) {
      $url = "https://www.amazon.co.jp/product-reviews/$($r.asin)/?reviewerType=all_reviews"
    }
    $endpoint = "$BaseUrl/api/admin/products/$pid/rescrape?url=$([uri]::EscapeDataString($url))&limit=$Limit"
    $res = Invoke-RestMethod -Method Post -Uri $endpoint -Headers @{ Authorization = "Bearer $token" }
    $results += [pscustomobject]@{
      productId = $pid; requestedUrl = $url; collected = $res.collected; upserted = $res.upserted; durationMs = $res.durationMs
    }
    Write-Host "[OK] $pid collected=$($res.collected) upserted=$($res.upserted)"
  } catch {
    Write-Warning "[NG] $pid : $($_.Exception.Message)"
    $results += [pscustomobject]@{ productId=$pid; requestedUrl=$url; collected=-1; upserted=-1; durationMs=0 }
  }
}
$results | Export-Csv -Path $OutCsv -NoTypeInformation -Encoding UTF8
Write-Host "[DONE] $OutCsv"
