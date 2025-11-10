Param(
    [string]$Root = "..\delivery\proof",
    [string]$Output = ".\features.csv"
)

$labelMap = @{
    "sakura"            = "SAKURA"
    "probably_sakura"   = "LIKELY"
    "probably_not_sakura" = "UNLIKELY"
    "not_sakura"        = "GENUINE"
}

if (!(Test-Path $Root)) {
    throw "Root path '$Root' was not found."
}

$records = New-Object System.Collections.Generic.List[object]

foreach ($label in $labelMap.Keys) {
    $baseDir = Join-Path $Root $label
    if (!(Test-Path $baseDir)) {
        Write-Host "[warn] label folder missing:" $baseDir
        continue
    }

    Get-ChildItem -Path $baseDir -Directory | ForEach-Object {
        $asinDir = $_
        $files = Get-ChildItem -Path $asinDir.FullName -Filter "*.ndjson" | Sort-Object LastWriteTime -Descending
        if (-not $files) { return }

        $latest = $files[0]
        Write-Host "[info] processing" $asinDir.Name "from" $label "file:" $latest.Name

        $reviews = New-Object System.Collections.Generic.List[object]
        Get-Content -LiteralPath $latest.FullName | ForEach-Object {
            $line = $_
            if ([string]::IsNullOrWhiteSpace($line)) { return }
            try {
                $obj = $line | ConvertFrom-Json
            } catch {
                Write-Host "[warn] skip invalid JSON line in $($latest.Name)"
                return
            }
            if ($obj.type -ne "review") { return }

            $rating = 0
            if ($null -ne $obj.rating) {
                $rating = [int][math]::Round([double]$obj.rating)
                if ($rating -lt 1) { $rating = 1 }
                if ($rating -gt 5) { $rating = 5 }
            }

            $body = ""
            if ($null -ne $obj.body) { $body = [string]$obj.body }

            $bodyLength = 0
            if ($null -ne $obj.bodyLength -and $obj.bodyLength -ne "") {
                $bodyLength = [int]$obj.bodyLength
            } elseif ($body) {
                $bodyLength = $body.Length
            }

            $reviews.Add([pscustomobject]@{
                Rating     = $rating
                Body       = $body
                BodyLength = $bodyLength
            })
        }

        if ($reviews.Count -eq 0) {
            Write-Host "[info] no reviews found for" $asinDir.Name
            return
        }

        $total = $reviews.Count
        $ratio5 = ($reviews | Where-Object { $_.Rating -ge 5 }).Count / [double]$total

        $lengths = $reviews | Select-Object -ExpandProperty BodyLength | Sort-Object
        $median = if ($lengths.Count % 2 -eq 1) {
            $lengths[[int][math]::Floor($lengths.Count / 2)]
        } else {
            ($lengths[($lengths.Count / 2) - 1] + $lengths[($lengths.Count / 2)]) / 2.0
        }
        $median = [math]::Max(1, [int][math]::Round($median))

        $hashCounts = @{}
        foreach ($review in $reviews) {
            $normalized = $review.Body -replace '\s+', ''
            $normalized = $normalized.ToLowerInvariant()
            if (-not $hashCounts.ContainsKey($normalized)) {
                $hashCounts[$normalized] = 0
            }
            $hashCounts[$normalized] += 1
        }
        $duplicateMax = ($hashCounts.Values | Measure-Object -Maximum).Maximum
        if (-not $duplicateMax) { $duplicateMax = 0 }

        $distBias = 100.0 * ((0.7 * $ratio5) + (0.3 * [Math]::Min(1.0, 40.0 / [double]$median)))
        $distBias = [math]::Round($distBias, 2)

        $records.Add([pscustomobject]@{
            product_id = $asinDir.Name
            label      = $labelMap[$label]
            dist_bias  = $distBias
            duplicates = [int]$duplicateMax
            cnt        = $total
        })
    }
}

if ($records.Count -eq 0) {
    Write-Host "[warn] no records produced; features.csv will not be created."
    return
}

$records | Export-Csv -Path $Output -NoTypeInformation -Encoding UTF8
Write-Host "[done] wrote features to" (Resolve-Path $Output)
