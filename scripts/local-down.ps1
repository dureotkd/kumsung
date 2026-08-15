$ErrorActionPreference = "Stop"

$projectDir = Split-Path -Parent $PSScriptRoot
$localRoot = Join-Path $projectDir ".local"
$runRoot = Join-Path $localRoot "run"
$postgresData = Join-Path $localRoot "data\postgres"
$runtimeRoot = Join-Path $env:LOCALAPPDATA "KumsungEncSmartPlatform\runtime"
$pgCtl = Join-Path $runtimeRoot "postgresql-17.11\pgsql\bin\pg_ctl.exe"

function Stop-TrackedProcess([string] $PidFile, [string] $ExpectedName, [string] $DisplayName) {
    if (-not (Test-Path -LiteralPath $PidFile)) { return }
    $processId = 0
    if ([int]::TryParse((Get-Content -LiteralPath $PidFile -Raw).Trim(), [ref] $processId)) {
        $process = Get-Process -Id $processId -ErrorAction SilentlyContinue
        if ($null -ne $process -and $process.ProcessName -eq $ExpectedName) {
            Write-Host "Stopping $DisplayName ..."
            Stop-Process -Id $processId
            Wait-Process -Id $processId -Timeout 15 -ErrorAction SilentlyContinue
        }
    }
    Remove-Item -LiteralPath $PidFile -ErrorAction SilentlyContinue
}

Stop-TrackedProcess (Join-Path $runRoot "app.pid") "java" "Spring Boot"
Stop-TrackedProcess (Join-Path $runRoot "mailpit.pid") "mailpit" "Mailpit"

if ((Test-Path -LiteralPath $pgCtl) -and (Test-Path -LiteralPath (Join-Path $postgresData "PG_VERSION"))) {
    & $pgCtl status -D $postgresData *> $null
    if ($LASTEXITCODE -eq 0) {
        Write-Host "Stopping PostgreSQL ..."
        & $pgCtl stop -D $postgresData -m fast -w
        if ($LASTEXITCODE -ne 0) { throw "PostgreSQL did not stop cleanly." }
    }
}
Remove-Item -LiteralPath (Join-Path $runRoot "postgres.pid") -ErrorAction SilentlyContinue

Write-Host "Local services are stopped. Database, uploads, and captured mail were preserved in $localRoot"
