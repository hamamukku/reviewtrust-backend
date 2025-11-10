param(
    [string]$CsvPath = "$PSScriptRoot\..\data\labels.csv",
    [string]$BaseUrl = "http://localhost:8080",
    [string]$TokenPath = "$PSScriptRoot\..\token.jwt",
    [string]$OutCsv = "$PSScriptRoot\products_map.csv"
)
Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

if (-not (Test-Path $CsvPath)) { throw "CSV not found: $CsvPath" }
if (-not (Test-Path $TokenPath)) { throw "Token not found: $TokenPath (run rt_login.ps1)" }
$token = Get-Content $TokenPath -Raw

$rows = Import-Csv $CsvPath
if (-not $rows) { throw "CSV appears empty: $CsvPath" }

$results = @()
foreach ($row in $rows) {
  $url = $row.url
  $name = if ($row.label) { $row.label } else { "Product" }
  if (-not $url) {
    Write-Warning "Skip row without URL"
    continue
  }
  try {
    $payload = @{ name = $name; url = $url } | ConvertTo-Json
    $res = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/admin/products" -Headers @{ Authorization = "Bearer $token" } -ContentType "application/json" -Body $payload
    $results += [pscustomobject]@{
      id = $res.id
      url = $res.url
      name = $res.name
      createdAt = $res.createdAt
    }
    Write-Host "[OK] registered -> $url"
  } catch {
    Write-Warning "Failed to register $url : $($_.Exception.Message)"
  }
}

if ($results.Count -gt 0) {
  $results | Export-Csv -Path $OutCsv -NoTypeInformation -Encoding UTF8
  Write-Host "[DONE] map -> $OutCsv"
} else {
  Write-Warning "No products registered"
}
