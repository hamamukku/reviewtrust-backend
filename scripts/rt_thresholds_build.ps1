Param(
    [string]$Features = ".\features.csv",
    [string]$OutYaml = ".\backend\src\main\resources\scoring\thresholds.yml"
)

if (!(Test-Path $Features)) {
    throw "Features file '$Features' not found."
}

$rows = Import-Csv -Path $Features
if (-not $rows -or $rows.Count -eq 0) {
    throw "Features file contains no rows."
}

$maxDistBias = ($rows | Measure-Object -Property dist_bias -Maximum).Maximum
$maxDup = ($rows | Measure-Object -Property duplicates -Maximum).Maximum
if (-not $maxDistBias) { $maxDistBias = 0 }
if (-not $maxDup) { $maxDup = 0 }

$distBiasSakura = [int][Math]::Round($maxDistBias * 0.80)
$dupSakura = [int][Math]::Round($maxDup * 0.50)

if ($distBiasSakura -gt 100) { $distBiasSakura = 100 }
if ($dupSakura -gt 100) { $dupSakura = 100 }

$distBiasLikely = 65
$dupLikely = 40
$distBiasUnlikely = 45

$distBiasSakuraNorm = "{0:0.2f}" -f ($distBiasSakura / 100.0)
$distBiasLikelyNorm = "{0:0.2f}" -f ($distBiasLikely / 100.0)
$distBiasUnlikelyNorm = "{0:0.2f}" -f ($distBiasUnlikely / 100.0)
$dupSakuraNorm = "{0:0.2f}" -f ($dupSakura / 100.0)
$dupLikelyNorm = "{0:0.2f}" -f ($dupLikely / 100.0)

$yaml = @()
$yaml += "weights:"
$yaml += "  dist_bias: 0.35"
$yaml += "  duplicates: 0.35"
$yaml += "  surge: 0.20"
$yaml += "  noise: 0.10"
$yaml += ""
$yaml += "rank:"
$yaml += "  a_max: 34"
$yaml += "  b_max: 64"
$yaml += ""
$yaml += "judge:"
$yaml += "  sakura:"
$yaml += "    dist_bias_min: $distBiasSakura"
$yaml += "    duplicates_min: $dupSakura"
$yaml += "  likely:"
$yaml += "    dist_bias_min: $distBiasLikely"
$yaml += "    duplicates_min: $dupLikely"
$yaml += "  unlikely:"
$yaml += "    dist_bias_min: $distBiasUnlikely"
$yaml += ""
$yaml += "dist_bias:"
$yaml += "  warn: 0.35"
$yaml += "  crit: 0.60"
$yaml += ""
$yaml += "duplicates:"
$yaml += "  warn: 0.30"
$yaml += "  crit: 0.50"
$yaml += ""
$yaml += "surge_z:"
$yaml += "  warn: 1.80"
$yaml += "  crit: 3.00"
$yaml += ""
$yaml += "noise:"
$yaml += "  warn: 0.25"
$yaml += "  crit: 0.50"
$yaml += ""
$yaml += "sakura_judge:"
$yaml += "  dist_bias_sakura: $distBiasSakuraNorm"
$yaml += "  dist_bias_likely: $distBiasLikelyNorm"
$yaml += "  dist_bias_unlikely: $distBiasUnlikelyNorm"
$yaml += "  duplicate_sakura: $dupSakuraNorm"
$yaml += "  duplicate_likely: $dupLikelyNorm"

$yaml | Set-Content -Path $OutYaml -Encoding UTF8
Write-Host "[done] thresholds written to" (Resolve-Path $OutYaml)
