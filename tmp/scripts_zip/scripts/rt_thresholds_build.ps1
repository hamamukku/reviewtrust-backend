param(
    [double]$DistWarn = 0.35, [double]$DistCrit = 0.60,
    [double]$DupWarn = 0.30,  [double]$DupCrit = 0.50,
    [double]$SurgeWarn = 1.80,[double]$SurgeCrit = 3.00,
    [double]$NoiseWarn = 0.25,[double]$NoiseCrit = 0.50,
    [double]$WDist = 0.35, [double]$WDup = 0.35, [double]$WSurge = 0.20, [double]$WNoise = 0.10,
    [string]$OutPath = "$PSScriptRoot\..\src\main\resources\scoring\thresholds.yml",
    [switch]$Force
)
Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$dir = Split-Path $OutPath
if (-not (Test-Path $dir)) { New-Item -ItemType Directory -Path $dir -Force | Out-Null }

$yml = @"
weights:
  dist_bias: $WDist
  duplicates: $WDup
  surge: $WSurge
  noise: $WNoise

dist_bias:
  warn: $DistWarn
  crit: $DistCrit

duplicates:
  warn: $DupWarn
  crit: $DupCrit

surge_z:
  warn: $SurgeWarn
  crit: $SurgeCrit

noise:
  warn: $NoiseWarn
  crit: $NoiseCrit
"@

if ((Test-Path $OutPath) -and -not $Force) {
  Write-Host "[SKIP] exists -> $OutPath (use -Force to overwrite)"
} else {
  $yml | Set-Content -Path $OutPath -Encoding UTF8
  Write-Host "[OK] thresholds -> $OutPath"
}
