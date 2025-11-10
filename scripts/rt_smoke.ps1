Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass -Force | Out-Null

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$backendDir = Split-Path -Parent $scriptDir
$repoRoot = Split-Path -Parent $backendDir
$deliveryDir = Join-Path $repoRoot "delivery\proof"

$summary = [ordered]@{
    "features"   = "NOT FOUND"
    "thresholds" = "NOT FOUND"
    "health"     = "NG"
    "scores"     = "SKIPPED"
}

# 修正点: 配列構築を明示
$featureCandidates = @()
$featureCandidates += Join-Path $backendDir "features.csv"
$featureCandidates += Join-Path $repoRoot "features.csv"

$featurePath = $featureCandidates | Where-Object { Test-Path $_ } | Select-Object -First 1
if ($featurePath) {
    $summary["features"] = "FOUND ($featurePath)"
} else {
    Write-Host "[hint] features.csv not found. Run the following command:" -ForegroundColor Yellow
    Write-Host "    pwsh backend\scripts\rt_features_from_ndjson.ps1 -Root ..\delivery\proof"
}

$thresholdPath = Join-Path $backendDir "src\main\resources\scoring\thresholds.yml"
if (Test-Path $thresholdPath) {
    $summary["thresholds"] = "FOUND ($thresholdPath)"
} else {
    Write-Host "[hint] thresholds.yml is missing. Run the following command:" -ForegroundColor Yellow
    Write-Host "    pwsh backend\scripts\rt_thresholds_build.ps1"
}

$healthOk = $false
try {
    $health = Invoke-RestMethod -Uri "http://localhost:8080/actuator/health" -Method Get -TimeoutSec 5
    if ($health.status -eq "UP") {
        $healthOk = $true
        $summary["health"] = "UP"
    } else {
        $summary["health"] = "NG (status=$($health.status))"
    }
} catch {
    $summary["health"] = "NG ($($_.Exception.Message))"
    Write-Host "[hint] Backend not reachable. Start it with:" -ForegroundColor Yellow
    Write-Host "    cd $backendDir"
    Write-Host "    gradle bootRun"
}

$productId = $null
if ($healthOk) {
    try {
        $productResponse = Invoke-RestMethod -Uri "http://localhost:8080/api/products?visible=true" -TimeoutSec 10
        if ($productResponse -is [System.Collections.IEnumerable]) {
            $first = $productResponse | Select-Object -First 1
            if ($first -and $first.id) {
                $productId = $first.id
            }
        } elseif ($productResponse.content) {
            $first = $productResponse.content | Select-Object -First 1
            if ($first -and $first.id) {
                $productId = $first.id
            }
        }
        if (-not $productId) {
            Write-Host "[hint] No visible products returned by /api/products. Add sample data then rerun." -ForegroundColor Yellow
            $summary["scores"] = "SKIPPED (no product)"
        }
    } catch {
        $summary["scores"] = "NG (/api/products failed: $($_.Exception.Message))"
    }
}

if ($productId) {
    try {
        $scores = Invoke-RestMethod -Uri "http://localhost:8080/api/products/$productId/scores" -TimeoutSec 10
        $judge = $scores.amazon.sakura_judge
        if ($judge -and @("SAKURA","LIKELY","UNLIKELY","GENUINE") -contains $judge) {
            $summary["scores"] = "OK ($judge)"
            if (-not (Test-Path $deliveryDir)) {
                New-Item -Path $deliveryDir -ItemType Directory | Out-Null
            }
            $outFile = Join-Path $deliveryDir ("scores_{0}.json" -f $productId)
            $scores | ConvertTo-Json -Depth 10 | Set-Content -Path $outFile -Encoding UTF8
            Write-Host "[info] Saved scores snapshot to $outFile" -ForegroundColor Cyan
        } else {
            $summary["scores"] = "NG (unexpected sakura_judge: $judge)"
            Write-Host "[hint] Ensure thresholds.yml is in place and backend restarted." -ForegroundColor Yellow
        }
    } catch {
        $summary["scores"] = "NG (/scores failed: $($_.Exception.Message))"
        Write-Host "[hint] Verify backend logs for errors while serving /scores." -ForegroundColor Yellow
    }
}

Write-Host ""
Write-Host "=== ReviewTrust Smoke Summary ===" -ForegroundColor Cyan
foreach ($key in $summary.Keys) {
    $value = $summary[$key]
    $color = "Yellow"
    switch -regex ($value) {
        "^OK" { $color = "Green" }
        "^UP" { $color = "Green" }
        "^FOUND" { $color = "Green" }
        "^NG" { $color = "Red" }
        "^SKIPPED" { $color = "Yellow" }
    }
    Write-Host ("{0,-12}: {1}" -f $key, $value) -ForegroundColor $color
}

Write-Host ""
Write-Host "If the backend is not running:"
Write-Host "    cd $backendDir"
Write-Host "    gradle bootRun"
Write-Host ""
Write-Host "Run smoke again via VS Code Task: Run ReviewTrust Smoke"
