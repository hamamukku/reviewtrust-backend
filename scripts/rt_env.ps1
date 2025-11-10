param(
    [string]$RepoRoot = (Get-Location).Path,
    [string]$Profile = "dev",
    [string]$DbContainer = "reviewtrust_db",
    [switch]$Quiet
)
Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function rtlog { param([string]$lvl, [string]$msg)
    if ($Quiet -and $lvl -eq "INFO") { return }
    $ts = (Get-Date).ToString("s")
    Write-Host "[$ts][$lvl] $msg"
}

function Ensure-EnvVar([string]$name, [string]$value) {
    $exists = Test-Path "Env:$name"
    $current = $exists ? (Get-Item "Env:$name").Value : $null
    if ([string]::IsNullOrWhiteSpace($current)) {
        Set-Item -Path "Env:$name" -Value $value
        rtlog INFO "Set $name"
    } else {
        rtlog INFO "$name already set"
    }
}

function Ensure-DockerDesktop {
    try { docker version *>$null; rtlog INFO "Docker CLI OK" }
    catch { rtlog ERROR "Docker is not available. Start Docker Desktop."; throw }
}

function Ensure-DbUp([string]$ContainerName) {
    $running = (docker ps --format "{{.Names}}") -contains $ContainerName
    if (-not $running) {
        $exists = (docker ps -a --format "{{.Names}}") -contains $ContainerName
        if ($exists) { rtlog INFO "Starting DB container $ContainerName ..."; docker start $ContainerName | Out-Null }
        else { rtlog ERROR "DB container $ContainerName not found."; throw "DB container not found" }
    } else { rtlog INFO "DB container $ContainerName running" }
}

# ── Export commonly used envs ────────────────────────────────────────────────
Ensure-EnvVar "SPRING_PROFILES_ACTIVE" $Profile
Ensure-EnvVar "SPRING_DATASOURCE_URL" "jdbc:postgresql://localhost:5432/reviewtrust"
Ensure-EnvVar "SPRING_DATASOURCE_USERNAME" "app"
Ensure-EnvVar "SPRING_DATASOURCE_PASSWORD" "app"
Ensure-EnvVar "CORS_ALLOWED_ORIGINS" "*"

# SECURITY_JWT_SECRET: generate strong default if missing
if (-not (Test-Path Env:SECURITY_JWT_SECRET) -or [string]::IsNullOrWhiteSpace((Get-Item Env:SECURITY_JWT_SECRET).Value)) {
    $bytes = New-Object byte[] 48
    (New-Object System.Security.Cryptography.RNGCryptoServiceProvider).GetBytes($bytes)
    $b64 = [Convert]::ToBase64String($bytes)
    Set-Item Env:SECURITY_JWT_SECRET $b64
    rtlog INFO "Generated SECURITY_JWT_SECRET (base64)"
}
Ensure-EnvVar "SECURITY_JWT_SECRET_ENCODING" "base64"

# ── Light checks ─────────────────────────────────────────────────────────────
Ensure-DockerDesktop
Ensure-DbUp -ContainerName $DbContainer
