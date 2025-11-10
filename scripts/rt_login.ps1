param(
    [string]$Email = "admin@example.com",
    [string]$Password,
    [string]$BaseUrl = "http://localhost:8080",
    [string]$OutFile = "$PSScriptRoot\..\token.jwt"
)
Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

if (-not $Password) {
  $sec = Read-Host -AsSecureString "Admin password"
  $Password = [Runtime.InteropServices.Marshal]::PtrToStringAuto([Runtime.InteropServices.Marshal]::SecureStringToBSTR($sec))
}
$body = @{ email=$Email; password=$Password } | ConvertTo-Json
try {
  $res = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/admin/login" -ContentType "application/json" -Body $body
  $token = $res.token
  if (-not $token) { throw "No token returned" }
  Set-Content -Path $OutFile -Value $token -NoNewline
  Write-Host "[OK] token saved -> $OutFile"
} catch {
  Write-Warning "Login failed: $($_.Exception.Message)"
  throw
}
