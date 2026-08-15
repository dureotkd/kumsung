$ErrorActionPreference = "Stop"

$projectDir = Split-Path -Parent $PSScriptRoot
$localRoot = Join-Path $projectDir ".local"
$dataRoot = Join-Path $localRoot "data"
$logRoot = Join-Path $localRoot "logs"
$runRoot = Join-Path $localRoot "run"
$runtimeRoot = Join-Path $env:LOCALAPPDATA "KumsungEncSmartPlatform\runtime"

$java = Join-Path $runtimeRoot "jdk-21.0.12+8\bin\java.exe"
$maven = Join-Path $runtimeRoot "apache-maven-3.9.11\bin\mvn.cmd"
$postgresBin = Join-Path $runtimeRoot "postgresql-17.11\pgsql\bin"
$pgCtl = Join-Path $postgresBin "pg_ctl.exe"
$initDb = Join-Path $postgresBin "initdb.exe"
$createdb = Join-Path $postgresBin "createdb.exe"
$psql = Join-Path $postgresBin "psql.exe"
$mailpit = Join-Path $runtimeRoot "mailpit-1.30.7\mailpit.exe"

if (-not (Test-Path -LiteralPath $java) -or
    -not (Test-Path -LiteralPath $maven) -or
    -not (Test-Path -LiteralPath $pgCtl) -or
    -not (Test-Path -LiteralPath $mailpit)) {
    Write-Host "Portable runtime is missing. Running local bootstrap first ..."
    & (Join-Path $PSScriptRoot "local-bootstrap.ps1")
    if ($LASTEXITCODE -ne 0) { throw "Local runtime bootstrap failed." }
}

New-Item -ItemType Directory -Force -Path $dataRoot, $logRoot, $runRoot | Out-Null

$localValues = @{}
$localEnvFile = Join-Path $projectDir ".env.local"
if (Test-Path -LiteralPath $localEnvFile) {
    Get-Content -LiteralPath $localEnvFile -Encoding UTF8 | ForEach-Object {
        if ($_ -match '^\s*([^#][^=]*)=(.*)$') {
            $localValues[$matches[1].Trim()] = $matches[2].Trim()
        }
    }
}

function Get-LocalSetting([string] $Name, [string] $DefaultValue) {
    $fileValue = [string] $localValues[$Name]
    if (-not [string]::IsNullOrWhiteSpace($fileValue)) { return $fileValue }
    $environmentValue = [Environment]::GetEnvironmentVariable($Name)
    if (-not [string]::IsNullOrWhiteSpace($environmentValue)) { return $environmentValue }
    return $DefaultValue
}

function Test-TcpPort([int] $Port) {
    $client = New-Object System.Net.Sockets.TcpClient
    try {
        $result = $client.BeginConnect("127.0.0.1", $Port, $null, $null)
        if (-not $result.AsyncWaitHandle.WaitOne(300)) { return $false }
        $client.EndConnect($result)
        return $true
    }
    catch { return $false }
    finally { $client.Dispose() }
}

function Test-TrackedProcess([string] $PidFile, [string] $ExpectedName) {
    if (-not (Test-Path -LiteralPath $PidFile)) { return $false }
    $processId = 0
    if (-not [int]::TryParse((Get-Content -LiteralPath $PidFile -Raw).Trim(), [ref] $processId)) { return $false }
    $process = Get-Process -Id $processId -ErrorAction SilentlyContinue
    return $null -ne $process -and $process.ProcessName -eq $ExpectedName
}

$serverPort = [int](Get-LocalSetting "SERVER_PORT" "8080")
$dbPort = [int](Get-LocalSetting "DB_PORT" "5432")
$smtpPort = [int](Get-LocalSetting "MAILPIT_SMTP_PORT" "1025")
$mailpitUiPort = [int](Get-LocalSetting "MAILPIT_UI_PORT" "8025")
$dbPassword = Get-LocalSetting "DB_PASSWORD" "kumsung_dev_password"
$adminEmail = Get-LocalSetting "ADMIN_EMAIL" "admin@localhost.test"
$adminPassword = Get-LocalSetting "ADMIN_PASSWORD" "local-admin-1234"
$appBaseUrl = Get-LocalSetting "APP_BASE_URL" "http://localhost:$serverPort"

if ($adminPassword.Length -lt 12) {
    throw "ADMIN_PASSWORD must contain at least 12 characters."
}

$postgresData = Join-Path $dataRoot "postgres"
$postgresLog = Join-Path $logRoot "postgres.log"
$postgresPid = Join-Path $runRoot "postgres.pid"
$postgresVersion = Join-Path $postgresData "PG_VERSION"

if (-not (Test-Path -LiteralPath $postgresVersion)) {
    if ((Get-ChildItem -LiteralPath $postgresData -Force -ErrorAction SilentlyContinue | Measure-Object).Count -gt 0) {
        throw "$postgresData contains incomplete PostgreSQL data. Move it aside and run again."
    }
    New-Item -ItemType Directory -Force -Path $postgresData | Out-Null
    $passwordFile = Join-Path $runRoot "postgres-password.tmp"
    try {
        [IO.File]::WriteAllText($passwordFile, $dbPassword, [Text.UTF8Encoding]::new($false))
        Write-Host "Initializing local PostgreSQL data ..."
        & $initDb --pgdata=$postgresData --username=kumsung --pwfile=$passwordFile --auth-host=scram-sha-256 --auth-local=trust --encoding=UTF8 --locale=C
        if ($LASTEXITCODE -ne 0) { throw "PostgreSQL initialization failed." }
    }
    finally {
        if (Test-Path -LiteralPath $passwordFile) { Remove-Item -LiteralPath $passwordFile }
    }
}

$postgresStatus = & $pgCtl status -D $postgresData 2>$null
$postgresRunning = $LASTEXITCODE -eq 0
if (-not $postgresRunning) {
    if (Test-TcpPort $dbPort) {
        throw "Port $dbPort is already in use. Stop that service or change DB_PORT in .env.local."
    }
    Write-Host "Starting PostgreSQL on port $dbPort ..."
    & $pgCtl start -D $postgresData -l $postgresLog -o "-p $dbPort" -w
    if ($LASTEXITCODE -ne 0) { throw "PostgreSQL failed to start. See $postgresLog" }
}

$postmasterPidFile = Join-Path $postgresData "postmaster.pid"
if (Test-Path -LiteralPath $postmasterPidFile) {
    $postmasterPid = (Get-Content -LiteralPath $postmasterPidFile -First 1).Trim()
    [IO.File]::WriteAllText($postgresPid, $postmasterPid, [Text.UTF8Encoding]::new($false))
}

$previousPgPassword = $env:PGPASSWORD
$env:PGPASSWORD = $dbPassword
try {
    $databaseExists = & $psql -h 127.0.0.1 -p $dbPort -U kumsung -d postgres -Atqc "select 1 from pg_database where datname='kumsung_enc'"
    if ($LASTEXITCODE -ne 0) { throw "Could not connect to local PostgreSQL." }
    if ($databaseExists -ne "1") {
        Write-Host "Creating kumsung_enc database ..."
        & $createdb -h 127.0.0.1 -p $dbPort -U kumsung kumsung_enc
        if ($LASTEXITCODE -ne 0) { throw "Could not create kumsung_enc database." }
    }
}
finally {
    $env:PGPASSWORD = $previousPgPassword
}

$mailpitPid = Join-Path $runRoot "mailpit.pid"
if (-not (Test-TrackedProcess $mailpitPid "mailpit")) {
    if ((Test-TcpPort $smtpPort) -or (Test-TcpPort $mailpitUiPort)) {
        throw "Mailpit port $smtpPort or $mailpitUiPort is already in use. Stop that service or change the ports in .env.local."
    }
    Write-Host "Starting Mailpit (SMTP $smtpPort, UI $mailpitUiPort) ..."
    $mailpitProcess = Start-Process -FilePath $mailpit `
        -ArgumentList @("--smtp", "127.0.0.1:$smtpPort", "--listen", "127.0.0.1:$mailpitUiPort", "--database", "data/mailpit.db") `
        -WorkingDirectory $localRoot -WindowStyle Hidden -PassThru `
        -RedirectStandardOutput (Join-Path $logRoot "mailpit.out.log") `
        -RedirectStandardError (Join-Path $logRoot "mailpit.err.log")
    [IO.File]::WriteAllText($mailpitPid, [string]$mailpitProcess.Id, [Text.UTF8Encoding]::new($false))
}

$appPid = Join-Path $runRoot "app.pid"
if (Test-TrackedProcess $appPid "java") {
    Write-Host "Application is already running."
    Write-Host "Web:      http://localhost:$serverPort"
    Write-Host "Mailpit:  http://localhost:$mailpitUiPort"
    exit 0
}
if (Test-TcpPort $serverPort) {
    throw "Port $serverPort is already in use. Stop that service or change SERVER_PORT in .env.local."
}

$jar = Get-ChildItem -LiteralPath (Join-Path $projectDir "target") -Filter "smart-platform-*.jar" -File -ErrorAction SilentlyContinue |
    Where-Object { $_.Name -notlike "*.original" } |
    Sort-Object LastWriteTime -Descending |
    Select-Object -First 1
$sourceFiles = @((Get-Item -LiteralPath (Join-Path $projectDir "pom.xml"))) +
    @(Get-ChildItem -LiteralPath (Join-Path $projectDir "src") -Recurse -File)
$newestSource = $sourceFiles | Sort-Object LastWriteTime -Descending | Select-Object -First 1
if ($null -eq $jar -or $newestSource.LastWriteTime -gt $jar.LastWriteTime) {
    Write-Host "Building the Spring Boot application ..."
    $previousJavaHome = $env:JAVA_HOME
    $env:JAVA_HOME = Split-Path -Parent (Split-Path -Parent $java)
    try {
        & $maven -f (Join-Path $projectDir "pom.xml") "-DskipTests" package
        if ($LASTEXITCODE -ne 0) { throw "Maven build failed." }
    }
    finally {
        $env:JAVA_HOME = $previousJavaHome
    }
    $jar = Get-ChildItem -LiteralPath (Join-Path $projectDir "target") -Filter "smart-platform-*.jar" -File |
        Where-Object { $_.Name -notlike "*.original" } |
        Sort-Object LastWriteTime -Descending |
        Select-Object -First 1
}

$managedEnvironment = @{
    "DB_URL" = "jdbc:postgresql://127.0.0.1:$dbPort/kumsung_enc"
    "DB_USERNAME" = "kumsung"
    "DB_PASSWORD" = $dbPassword
    "SERVER_PORT" = [string]$serverPort
    "SERVER_ADDRESS" = "127.0.0.1"
    "MAIL_HOST" = "127.0.0.1"
    "MAIL_PORT" = [string]$smtpPort
    "MAIL_PROVIDER" = "MAILPIT"
    "MAIL_USERNAME" = "system@localhost"
    "MAIL_PASSWORD" = ""
    "MAIL_AUTH" = "false"
    "MAIL_STARTTLS" = "false"
    "MAIL_FROM" = (Get-LocalSetting "MAIL_FROM" "system@localhost")
    "APP_BASE_URL" = $appBaseUrl
    "UPLOAD_DIR" = (Join-Path $dataRoot "uploads")
    "ADMIN_EMAIL" = $adminEmail
    "ADMIN_PASSWORD" = $adminPassword
    "QUOTE_RECIPIENT" = (Get-LocalSetting "QUOTE_RECIPIENT" "estimate@localhost.test")
    "SUPPORT_RECIPIENT" = (Get-LocalSetting "SUPPORT_RECIPIENT" "support@localhost.test")
    "SUPPORT_EMAIL" = (Get-LocalSetting "SUPPORT_EMAIL" "support@localhost.test")
    "MALWARE_SCAN_ENABLED" = "false"
    "GOOGLE_SHEETS_ENABLED" = "false"
}

$previousEnvironment = @{}
foreach ($entry in $managedEnvironment.GetEnumerator()) {
    $previousEnvironment[$entry.Key] = [Environment]::GetEnvironmentVariable($entry.Key)
    [Environment]::SetEnvironmentVariable($entry.Key, [string]$entry.Value)
}

try {
    Write-Host "Starting Spring Boot on port $serverPort ..."
    $appProcess = Start-Process -FilePath $java -ArgumentList "-jar `"$($jar.FullName)`"" `
        -WorkingDirectory $projectDir -WindowStyle Hidden -PassThru `
        -RedirectStandardOutput (Join-Path $logRoot "app.out.log") `
        -RedirectStandardError (Join-Path $logRoot "app.err.log")
    [IO.File]::WriteAllText($appPid, [string]$appProcess.Id, [Text.UTF8Encoding]::new($false))
}
finally {
    foreach ($entry in $previousEnvironment.GetEnumerator()) {
        [Environment]::SetEnvironmentVariable($entry.Key, $entry.Value)
    }
}

$healthUrl = "http://127.0.0.1:$serverPort/actuator/health"
$ready = $false
for ($attempt = 1; $attempt -le 90; $attempt++) {
    try {
        $health = Invoke-RestMethod -Uri $healthUrl -TimeoutSec 2
        if ($health.status -eq "UP") { $ready = $true; break }
    }
    catch {
        if (-not (Get-Process -Id $appProcess.Id -ErrorAction SilentlyContinue)) { break }
    }
    Start-Sleep -Seconds 1
}

if (-not $ready) {
    Write-Host ""
    Write-Host "Application did not become healthy. Recent logs:"
    Get-Content -LiteralPath (Join-Path $logRoot "app.out.log") -Tail 80 -ErrorAction SilentlyContinue
    Get-Content -LiteralPath (Join-Path $logRoot "app.err.log") -Tail 80 -ErrorAction SilentlyContinue
    throw "Spring Boot startup failed."
}

Write-Host ""
Write-Host "Kumsung ENC Smart Platform is ready."
Write-Host "Web:       http://localhost:$serverPort"
Write-Host "Admin:     http://localhost:$serverPort/admin.html"
Write-Host "Mailpit:   http://localhost:$mailpitUiPort"
Write-Host "Admin ID:  $adminEmail"
Write-Host "Admin PW:  $adminPassword"
Write-Host "Logs:      $logRoot"
Write-Host "Stop:      .\scripts\local-down.ps1"
