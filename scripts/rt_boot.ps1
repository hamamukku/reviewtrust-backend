param(
    [string]$Profile = "dev",
    [string]$DbContainer = "reviewtrust_db",
    [int]$Port = 8080,
    [switch]$Debug
)
. $PSScriptRoot\rt_env.ps1 -Profile $Profile -DbContainer $DbContainer

rtlog INFO "Booting backend (profile=$Profile, port=$Port)"
# Kill stray java
Get-Process java -ErrorAction SilentlyContinue | Stop-Process -Force -ErrorAction SilentlyContinue

$gradleArgs = @()
$gradleArgs += "-Dsecurity.jwt.secret=$env:SECURITY_JWT_SECRET"
if ($Debug) {
  $gradleArgs += "-Dlogging.level.org.springframework.security=DEBUG"
  $gradleArgs += "-Dcom.hamas.reviewtrust=DEBUG"
}
$env:SERVER_PORT = "$Port"
rtlog INFO "Running: gradle $($gradleArgs -join ' ') bootRun"
gradle $gradleArgs bootRun
