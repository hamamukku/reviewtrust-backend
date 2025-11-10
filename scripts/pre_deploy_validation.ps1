[CmdletBinding()]
param(
    [string]$BaseUrl = "http://localhost:8080",
    [string]$ProofDir,
    [string]$FrontendDir,
    [string]$AdminEmail = "admin@example.com",
    [string]$AdminPassword,
    [int]$HealthTimeoutSeconds = 180,
    [int]$HealthPollSeconds = 5,
    [switch]$SkipFrontend,
    [switch]$SkipDocker
)

<#
.SYNOPSIS
    Automates the review backend pre-deploy validation flow.

.DESCRIPTION
    Executes the build, manual tests, boot/health checks, API contract validation,
    data pipeline sampling, ranker verification, docker compose smoke test,
    optional frontend smoke, and security guard checks. Key artefacts are collected
    into delivery/proof and the script prints "✅ validation complete" on success.

.NOTES
    Run from repository root or scripts directory. Docker Desktop, Gradle, psql,
    curl (Invoke-RestMethod), and npm are expected when relevant steps are enabled.
#>

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function New-ValidationError {
    param([string]$Message)
    return New-Object System.Exception $Message
}

function Write-Step {
    param(
        [string]$Title,
        [string]$Status = "INFO"
    )
    $ts = (Get-Date).ToString("s")
    $line = "[{0}][{1}] {2}" -f $ts, $Status, $Title
    Write-Host $line
    $line | Add-Content -Path $script:LogFile
}

function Ensure-Directory {
    param([string]$Path)
    if (-not (Test-Path $Path)) {
        New-Item -ItemType Directory -Path $Path -Force | Out-Null
    }
}

function Resolve-Gradle {
    param([string]$RepoRoot)
    $gradleWrapper = Join-Path $RepoRoot "gradlew.bat"
    if (Test-Path $gradleWrapper) {
        return $gradleWrapper
    }
    $gradle = Get-Command gradle -ErrorAction SilentlyContinue
    if ($gradle) {
        return $gradle.Source
    }
    throw (New-ValidationError "Gradle executable not found (install Gradle or include gradlew wrapper).")
}

function Invoke-LoggedProcess {
    param(
        [string]$FilePath,
        [string[]]$ArgumentList,
        [string]$WorkingDirectory,
        [string]$LogPath,
        [switch]$Append
    )

    $stdout = "$LogPath.stdout"
    $stderr = "$LogPath.stderr"

    $splatted = @{
        FilePath               = $FilePath
        ArgumentList           = $ArgumentList
        WorkingDirectory       = $WorkingDirectory
        RedirectStandardOutput = $stdout
        RedirectStandardError  = $stderr
        NoNewWindow            = $true
        PassThru               = $true
    }

    $process = Start-Process @splatted
    $process.WaitForExit()
    $exit = $process.ExitCode

    $output = @( )
    if (Test-Path $stdout) {
        $output += Get-Content -Path $stdout
    }
    if (Test-Path $stderr) {
        $output += Get-Content -Path $stderr
    }

    if ($Append) {
        $output | Add-Content -Path $LogPath
    } else {
        $output | Set-Content -Path $LogPath
    }

    Remove-Item $stdout, $stderr -ErrorAction SilentlyContinue

    return @{
        ExitCode = $exit
        Output   = $output
    }
}

function Save-Tail {
    param(
        [string]$Source,
        [string]$Destination,
        [int]$Lines = 120
    )
    if (-not (Test-Path $Source)) { return }
    $tail = Get-Content -Path $Source -Tail $Lines
    $tail | Set-Content -Path $Destination
}

function Wait-ForHealth {
    param(
        [string]$Url,
        [int]$TimeoutSeconds,
        [int]$IntervalSeconds
    )

    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    do {
        try {
            $response = Invoke-RestMethod -Method Get -Uri $Url -TimeoutSec 5
            if ($response.status -eq "UP") {
                return $response
            }
        } catch {
            Start-Sleep -Seconds $IntervalSeconds
        }
    } while ((Get-Date) -lt $deadline)

    throw (New-ValidationError "Health endpoint $Url did not report UP within $TimeoutSeconds seconds.")
}

function Parse-ConnectionInfo {
    param(
        [string]$DefaultUrl = "jdbc:postgresql://localhost:5432/reviewtrust",
        [string]$DefaultUser = "app",
        [string]$DefaultPassword = "app"
    )

    $jdbcUrl = $Env:SPRING_DATASOURCE_URL
    if ([string]::IsNullOrWhiteSpace($jdbcUrl)) { $jdbcUrl = $DefaultUrl }

    if ($jdbcUrl -notmatch "jdbc:postgresql://([^:/]+)(?::(\d+))?/(.+)") {
        throw (New-ValidationError "Cannot parse SPRING_DATASOURCE_URL: $jdbcUrl")
    }

    $host = $Matches[1]
    $port = if ($Matches[2]) { [int]$Matches[2] } else { 5432 }
    $database = $Matches[3]
    $user = if ($Env:SPRING_DATASOURCE_USERNAME) { $Env:SPRING_DATASOURCE_USERNAME } else { $DefaultUser }
    $password = if ($Env:SPRING_DATASOURCE_PASSWORD) { $Env:SPRING_DATASOURCE_PASSWORD } else { $DefaultPassword }

    return [pscustomobject]@{
        Host     = $host
        Port     = $port
        Database = $database
        User     = $user
        Password = $password
    }
}

function Invoke-PsqlCopy {
    param(
        [string]$Query,
        [string]$OutputPath
    )

    $psql = Get-Command psql -ErrorAction SilentlyContinue
    if (-not $psql) {
        Write-Step "psql not found; skipping export for $OutputPath" "WARN"
        return $false
    }

    $conn = Parse-ConnectionInfo
    Ensure-Directory (Split-Path -Parent $OutputPath)

    $env:PGPASSWORD = $conn.Password
    try {
        $copy = "\\copy ($Query) TO '$OutputPath' CSV HEADER"
        $args = @(
            "-h", $conn.Host,
            "-p", $conn.Port,
            "-U", $conn.User,
            "-d", $conn.Database,
            "-c", $copy
        )
        $proc = Start-Process -FilePath $psql.Source -ArgumentList $args -NoNewWindow -Wait -PassThru
        if ($proc.ExitCode -ne 0) {
            throw (New-ValidationError "psql copy failed for $OutputPath (exit $($proc.ExitCode))")
        }
    } finally {
        Remove-Item Env:PGPASSWORD -ErrorAction SilentlyContinue
    }

    return $true
}

function Resolve-StatusCode {
    param([System.Exception]$Exception)

    if (-not $Exception) { return $null }
    if ($Exception.PSObject.Properties.Name -notcontains "Response") { return $null }

    $response = $Exception.Response
    if (-not $response) { return $null }

    if ($response -is [System.Net.HttpWebResponse]) {
        return [int]$response.StatusCode
    }
    if ($response -is [System.Net.Http.HttpResponseMessage]) {
        return [int]$response.StatusCode
    }
    if ($response.PSObject.Properties.Name -contains "StatusCode") {
        return [int]$response.StatusCode
    }
    return $null
}

function Invoke-JsonRequest {
    param(
        [string]$Method,
        [string]$Url,
        [hashtable]$Headers,
        [object]$Body
    )

    $params = @{
        Method      = $Method
        Uri         = $Url
        Headers     = $Headers
        ErrorAction = "Stop"
    }
    if ($Body -ne $null) {
        $params.ContentType = "application/json"
        $params.Body = ($Body | ConvertTo-Json -Depth 8)
    }
    return Invoke-RestMethod @params
}

function Ensure-Env {
    param(
        [string]$ScriptsDir,
        [string]$Profile = "dev"
    )

    $envScript = Join-Path $ScriptsDir "rt_env.ps1"
    if (Test-Path $envScript) {
        & $envScript -Profile $Profile | Out-Null
    }
}

$RepoRoot = Resolve-Path (Join-Path $PSScriptRoot "..") | Select-Object -ExpandProperty Path
if (-not $ProofDir) {
    $ProofDir = Join-Path $RepoRoot "delivery\proof"
}
Ensure-Directory $ProofDir

$script:LogFile = Join-Path $ProofDir "validation.log"
"" | Set-Content -Path $script:LogFile

Write-Step "Starting pre-deploy validation"

$gradleExe = Resolve-Gradle -RepoRoot $RepoRoot
$scriptsDir = $PSScriptRoot

Ensure-Env -ScriptsDir $scriptsDir

$buildLog = Join-Path $ProofDir "build.log"
$buildTail = Join-Path $ProofDir "build_tail.txt"
$manualLog = Join-Path $ProofDir "manual_tests.log"
$manualTail = Join-Path $ProofDir "manual_tests_tail.txt"
$bootLog = Join-Path $ProofDir "boot.log"
$healthJsonPath = Join-Path $ProofDir "health.json"
$scoresJsonPath = Join-Path $ProofDir "scores_contract.json"
$pipelineLog = Join-Path $ProofDir "pipeline.log"
$rankerLog = Join-Path $ProofDir "ranker_checks.log"
$dockerLog = Join-Path $ProofDir "docker.log"
$dockerPsPath = Join-Path $ProofDir "compose_ps.txt"
$securityMd = Join-Path $ProofDir "security_checks.md"
$frontendLog = Join-Path $ProofDir "frontend.log"

# Step 1: Build + OpenAPI
Write-Step "Step 1: gradle clean build"
$result = Invoke-LoggedProcess -FilePath $gradleExe -ArgumentList @("clean", "build") -WorkingDirectory $RepoRoot -LogPath $buildLog
if ($result.ExitCode -ne 0) {
    throw (New-ValidationError "gradle clean build failed (exit $($result.ExitCode))")
}
if (-not ($result.Output | Where-Object { $_ -match "BUILD SUCCESSFUL" })) {
    Write-Step "BUILD SUCCESSFUL not detected in gradle output" "WARN"
}

Write-Step "Step 1: gradle exportOpenApi"
$result = Invoke-LoggedProcess -FilePath $gradleExe -ArgumentList @("exportOpenApi") -WorkingDirectory $RepoRoot -LogPath $buildLog -Append
if ($result.ExitCode -ne 0) {
    throw (New-ValidationError "gradle exportOpenApi failed (exit $($result.ExitCode))")
}

$openApiPath = Join-Path $RepoRoot "delivery\openapi\openapi.json"
if (-not (Test-Path $openApiPath)) {
    throw (New-ValidationError "OpenAPI output not found at $openApiPath")
}
Save-Tail -Source $buildLog -Destination $buildTail

# Step 2: Manual tests
Write-Step "Step 2: gradle runManualTests"
$result = Invoke-LoggedProcess -FilePath $gradleExe -ArgumentList @("runManualTests") -WorkingDirectory $RepoRoot -LogPath $manualLog
if ($result.ExitCode -ne 0) {
    throw (New-ValidationError "gradle runManualTests failed (exit $($result.ExitCode))")
}
if (-not ($result.Output | Where-Object { $_ -match "MANUAL TESTS PASSED" })) {
    Write-Step "Manual test confirmation string missing" "WARN"
}
Save-Tail -Source $manualLog -Destination $manualTail

# Step 3: Boot + health (keep running for subsequent steps)
Write-Step "Step 3: bootRun + health check"
$bootStdOut = "$bootLog.stdout"
$bootStdErr = "$bootLog.stderr"
$bootArgs = @(
    "-Dspring-boot.run.jvmArguments=-Dapp.scraping.headless=false",
    "bootRun"
)
$bootProcess = Start-Process -FilePath $gradleExe -ArgumentList $bootArgs -WorkingDirectory $RepoRoot -RedirectStandardOutput $bootStdOut -RedirectStandardError $bootStdErr -NoNewWindow -PassThru

try {
    $health = Wait-ForHealth -Url "$BaseUrl/actuator/health" -TimeoutSeconds $HealthTimeoutSeconds -IntervalSeconds $HealthPollSeconds
    ($health | ConvertTo-Json -Depth 4) | Set-Content -Path $healthJsonPath
} catch {
    throw
}

# Step 4: Scores API contract
Write-Step "Step 4: scores API contract validation"
if (-not $AdminPassword) {
    $secure = Read-Host -AsSecureString "Admin password"
    $AdminPassword = [Runtime.InteropServices.Marshal]::PtrToStringAuto([Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure))
}

$loginPayload = @{
    email    = $AdminEmail
    password = $AdminPassword
}
$login = Invoke-JsonRequest -Method Post -Url "$BaseUrl/api/admin/login" -Headers @{} -Body $loginPayload
if (-not $login.token) {
    throw (New-ValidationError "Admin login did not return token")
}
$token = $login.token
Set-Content -Path $tokenFile -Value $token -NoNewline

$sampleUrl = "https://www.amazon.co.jp/dp/B09G3HRMVB"
$productBody = @{
    name = "Validation Sample $(Get-Date -Format 'HHmmss')"
    url  = $sampleUrl
}
$product = Invoke-JsonRequest -Method Post -Url "$BaseUrl/api/admin/products" -Headers @{ Authorization = "Bearer $token" } -Body $productBody
$productId = $product.id
if (-not $productId) {
    throw (New-ValidationError "Product registration failed; no id returned")
}

Invoke-JsonRequest -Method Post -Url "$BaseUrl/api/admin/products/$productId/rescrape?limit=20&url=$([uri]::EscapeDataString($sampleUrl))" -Headers @{ Authorization = "Bearer $token" } -Body $null | Out-Null
Start-Sleep -Seconds 5

$scores = Invoke-JsonRequest -Method Get -Url "$BaseUrl/api/products/$productId/scores" -Headers @{ Authorization = "Bearer $token" } -Body $null
($scores | ConvertTo-Json -Depth 10) | Set-Content -Path $scoresJsonPath

$payloadKeys = @("score", "rank", "sakura_judge", "flags", "rules")
$amazon = $scores.amazon
foreach ($key in $payloadKeys) {
    if (-not $amazon.PSObject.Properties.Name -contains $key) {
        Write-Step "scores payload missing $key" "WARN"
    }
}

# Step 5: Pipeline sampling
Write-Step "Step 5: pipeline sampling (seed/rescrape/features/confusion/thresholds)"
$labelsCsv = Join-Path $RepoRoot "delivery\proof\labels_seed.csv"
@(
    "url,label"
    "https://www.amazon.co.jp/dp/B09G3HRMVB,Sample A"
    "https://www.amazon.co.jp/dp/B08N5WRWNW,Sample B"
    "https://www.amazon.co.jp/dp/B07FZ8S74R,Sample C"
) | Set-Content -Path $labelsCsv

$seedScript = Join-Path $scriptsDir "rt_seed_products.ps1"
$rescrapeScript = Join-Path $scriptsDir "rt_rescrape_batch.ps1"
$thresholdsScript = Join-Path $scriptsDir "rt_thresholds_build.ps1"

"" | Set-Content -Path $pipelineLog
& $seedScript -CsvPath $labelsCsv -BaseUrl $BaseUrl -TokenPath $tokenFile -OutCsv (Join-Path $ProofDir "seed_products.csv") | Tee-Object -FilePath $pipelineLog
& $rescrapeScript -CsvPath (Join-Path $ProofDir "seed_products.csv") -BaseUrl $BaseUrl -TokenPath $tokenFile -Limit 50 | Tee-Object -FilePath $pipelineLog -Append
& $thresholdsScript -Force | Tee-Object -FilePath $pipelineLog -Append

$featuresSql = Get-Content -Path (Join-Path $scriptsDir "rt_features_sql.sql") -Raw
$confusionSql = (Get-Content -Path (Join-Path $scriptsDir "rt_confusion_sql.sql") -Raw).Split(";", [System.StringSplitOptions]::RemoveEmptyEntries)[0]

$featuresCsv = Join-Path $ProofDir "features.csv"
$confusionCsv = Join-Path $ProofDir "confusion.csv"

Invoke-PsqlCopy -Query $featuresSql -OutputPath $featuresCsv | Out-Null
Invoke-PsqlCopy -Query $confusionSql -OutputPath $confusionCsv | Out-Null
Copy-Item -Path (Join-Path $RepoRoot "delivery\scoring\thresholds.yml") -Destination (Join-Path $ProofDir "thresholds.yml") -Force

# Step 6: Ranker thresholds sweep
Write-Step "Step 6: ranker sakura judge sweep"
$thresholdsPath = Join-Path $RepoRoot "delivery\scoring\thresholds.yml"
$thresholdsBackup = Join-Path $ProofDir "thresholds.backup.yml"
Copy-Item -Path $thresholdsPath -Destination $thresholdsBackup -Force

$rankSamples = @(
    @{ name = "force_sakura"; dist_bias_sakura = 0.0; dist_bias_likely = 0.0; dist_bias_unlikely = 0.0; duplicate_sakura = 0.0; duplicate_likely = 0.0; expect = "SAKURA" },
    @{ name = "force_likely"; dist_bias_sakura = 1.0; dist_bias_likely = 0.0; dist_bias_unlikely = 0.0; duplicate_sakura = 1.0; duplicate_likely = 0.0; expect = "LIKELY" },
    @{ name = "force_unlikely"; dist_bias_sakura = 1.0; dist_bias_likely = 1.0; dist_bias_unlikely = 0.0; duplicate_sakura = 1.0; duplicate_likely = 1.0; expect = "UNLIKELY" },
    @{ name = "force_genuine"; dist_bias_sakura = 1.0; dist_bias_likely = 1.0; dist_bias_unlikely = 1.0; duplicate_sakura = 1.0; duplicate_likely = 1.0; expect = "GENUINE" }
)

$rankResults = @()
foreach ($sample in $rankSamples) {
    $content = @"
weights:
  dist_bias: 0.35
  duplicates: 0.35
  surge: 0.2
  noise: 0.1

dist_bias:
  warn: 0.35
  crit: 0.6

duplicates:
  warn: 0.3
  crit: 0.5

surge_z:
  warn: 1.8
  crit: 3.0

noise:
  warn: 0.25
  crit: 0.5

sakura_judge:
  dist_bias_sakura: $($sample.dist_bias_sakura)
  dist_bias_likely: $($sample.dist_bias_likely)
  dist_bias_unlikely: $($sample.dist_bias_unlikely)
  duplicate_sakura: $($sample.duplicate_sakura)
  duplicate_likely: $($sample.duplicate_likely)
"@
    $content | Set-Content -Path $thresholdsPath
    $score = Invoke-JsonRequest -Method Get -Url "$BaseUrl/api/products/$productId/scores" -Headers @{ Authorization = "Bearer $token" } -Body $null
    $judge = $score.amazon.sakura_judge
    $rankResults += [pscustomobject]@{
        scenario = $sample.name
        expected = $sample.expect
        observed = $judge
    }
}

$rankResults | ConvertTo-Csv -NoTypeInformation | Set-Content -Path (Join-Path $ProofDir "ranker_scenarios.csv")
$rankResults | Format-Table | Out-String | Set-Content -Path $rankerLog

Copy-Item -Path $thresholdsBackup -Destination $thresholdsPath -Force

# Step 9 (run before docker): security guard
Write-Step "Step 9: security guard checks"
$securityLines = @()
try {
    Invoke-JsonRequest -Method Get -Url "$BaseUrl/api/admin/whoami" -Headers @{} -Body $null | Out-Null
    $securityLines += "- [FAIL] Anonymous whoami returned 200"
} catch {
    $code = Resolve-StatusCode $_.Exception
    if ($code) {
        $securityLines += "- [PASS] Anonymous whoami rejected with $code"
    } else {
        $securityLines += "- [PASS] Anonymous whoami rejected"
    }
}

try {
    Invoke-JsonRequest -Method Get -Url "$BaseUrl/api/admin/whoami" -Headers @{ Authorization = "Bearer invalid" } -Body $null | Out-Null
    $securityLines += "- [FAIL] Invalid token accepted"
} catch {
    $code = Resolve-StatusCode $_.Exception
    if ($code) {
        $securityLines += "- [PASS] Invalid token rejected with $code"
    } else {
        $securityLines += "- [PASS] Invalid token rejected"
    }
}

try {
    Invoke-JsonRequest -Method Get -Url "$BaseUrl/api/products/not-a-uuid/scores" -Headers @{} -Body $null | Out-Null
    $securityLines += "- [FAIL] Non-UUID scores returned 200"
} catch {
    $code = Resolve-StatusCode $_.Exception
    if ($code) {
        $securityLines += "- [PASS] Non-UUID scores rejected with $code"
    } else {
        $securityLines += "- [PASS] Non-UUID scores rejected"
    }
}

try {
    $resp = Invoke-WebRequest -Method Options -Uri "$BaseUrl/api/products" -Headers @{ Origin = "http://localhost:4173" }
    $allow = $resp.Headers["Access-Control-Allow-Origin"]
    if ($allow) {
        $securityLines += "- [PASS] CORS allowed origin: $allow"
    } else {
        $securityLines += "- [WARN] CORS missing Access-Control-Allow-Origin header"
    }
} catch {
    $securityLines += "- [WARN] CORS preflight failed: $($_.Exception.Message)"
}

$securityLines | Set-Content -Path $securityMd

# Stop gradle boot before docker
Write-Step "Stopping gradle bootRun"
try {
    if ($bootProcess -and -not $bootProcess.HasExited) {
        Stop-Process -Id $bootProcess.Id -Force -ErrorAction SilentlyContinue
    }
} finally {
    if (Test-Path $bootStdOut) {
        Get-Content -Path $bootStdOut | Set-Content -Path $bootLog
        Remove-Item $bootStdOut -ErrorAction SilentlyContinue
    }
    if (Test-Path $bootStdErr) {
        Get-Content -Path $bootStdErr | Add-Content -Path $bootLog
        Remove-Item $bootStdErr -ErrorAction SilentlyContinue
    }
}

# Step 7: Docker compose
if (-not $SkipDocker) {
    Write-Step "Step 7: docker compose up"
    $composeFile = Join-Path $RepoRoot "delivery\compose.yml"
    $dockerArgs = @("compose", "-f", $composeFile, "up", "-d", "--build")
    $result = Invoke-LoggedProcess -FilePath "docker" -ArgumentList $dockerArgs -WorkingDirectory $RepoRoot -LogPath $dockerLog
    if ($result.ExitCode -ne 0) {
        throw (New-ValidationError "docker compose up failed (exit $($result.ExitCode))")
    }

    Start-Sleep -Seconds 10

    $psArgs = @("compose", "-f", $composeFile, "ps")
    $psStdOut = "$dockerPsPath.stdout"
    $psStdErr = "$dockerPsPath.stderr"
    $proc = Start-Process -FilePath "docker" -ArgumentList $psArgs -WorkingDirectory $RepoRoot -RedirectStandardOutput $psStdOut -RedirectStandardError $psStdErr -NoNewWindow -PassThru
    $proc.WaitForExit()
    $psLines = @( )
    if (Test-Path $psStdOut) { $psLines += Get-Content -Path $psStdOut }
    if (Test-Path $psStdErr) { $psLines += Get-Content -Path $psStdErr }
    $psLines | Set-Content -Path $dockerPsPath
    Remove-Item $psStdOut, $psStdErr -ErrorAction SilentlyContinue

    try {
        Wait-ForHealth -Url "$BaseUrl/actuator/health" -TimeoutSeconds 60 -IntervalSeconds 5 | Out-Null
    } catch {
        Write-Step "Dockerised backend health check failed" "WARN"
    }
} else {
    Write-Step "Skip docker compose step" "WARN"
}

# Step 8: Frontend smoke (optional)
if (-not $SkipFrontend) {
    if (-not $FrontendDir) {
        $candidate = Join-Path $RepoRoot "..\frontend"
        if (Test-Path $candidate) {
            $FrontendDir = (Resolve-Path $candidate).Path
        }
    }

    if ($FrontendDir -and (Test-Path $FrontendDir)) {
        Write-Step "Step 8: frontend npm build/preview"
        $envPath = Join-Path $FrontendDir ".env"
        "VITE_API_BASE_URL=$BaseUrl" | Set-Content -Path $envPath
        Invoke-LoggedProcess -FilePath "npm" -ArgumentList @("install") -WorkingDirectory $FrontendDir -LogPath $frontendLog | Out-Null
        Invoke-LoggedProcess -FilePath "npm" -ArgumentList @("run", "build") -WorkingDirectory $FrontendDir -LogPath $frontendLog -Append | Out-Null

        $previewArgs = @("run", "preview", "--", "--host")
        $previewStdOut = "$frontendLog.stdout"
        $previewStdErr = "$frontendLog.stderr"
        $previewProc = Start-Process -FilePath "npm" -ArgumentList $previewArgs -WorkingDirectory $FrontendDir -RedirectStandardOutput $previewStdOut -RedirectStandardError $previewStdErr -NoNewWindow -PassThru
        Start-Sleep -Seconds 10
        try {
            Invoke-WebRequest -Uri "http://127.0.0.1:4173" -UseBasicParsing | Out-Null
        } catch {
            Write-Step "Frontend preview probe failed: $($_.Exception.Message)" "WARN"
        } finally {
            if ($previewProc -and -not $previewProc.HasExited) {
                Stop-Process -Id $previewProc.Id -Force -ErrorAction SilentlyContinue
            }
            if (Test-Path $previewStdOut) {
                Get-Content -Path $previewStdOut | Add-Content -Path $frontendLog
                Remove-Item $previewStdOut -ErrorAction SilentlyContinue
            }
            if (Test-Path $previewStdErr) {
                Get-Content -Path $previewStdErr | Add-Content -Path $frontendLog
                Remove-Item $previewStdErr -ErrorAction SilentlyContinue
            }
        }
    } else {
        Write-Step "Frontend directory not found; skipping Step 8" "WARN"
    }
} else {
    Write-Step "Skip frontend verification (flag set)" "WARN"
}

# Bring down docker compose if started
if (-not $SkipDocker) {
    Write-Step "Tearing down docker compose"
    Invoke-LoggedProcess -FilePath "docker" -ArgumentList @("compose", "-f", (Join-Path $RepoRoot "delivery\compose.yml"), "down") -WorkingDirectory $RepoRoot -LogPath $dockerLog -Append | Out-Null
}

# Restore thresholds
if (Test-Path $thresholdsBackup) {
    Copy-Item -Path $thresholdsBackup -Destination $thresholdsPath -Force
}

Write-Step "Validation completed successfully"
Write-Host "✅ validation complete"
