$ErrorActionPreference = "Stop"

if (-not [Environment]::Is64BitOperatingSystem) {
    throw "The portable local runtime requires 64-bit Windows."
}

$runtimeRoot = Join-Path $env:LOCALAPPDATA "KumsungEncSmartPlatform\runtime"
$downloadRoot = Join-Path $runtimeRoot "downloads"
New-Item -ItemType Directory -Force -Path $runtimeRoot, $downloadRoot | Out-Null

$jdkRoot = Join-Path $runtimeRoot "jdk-21.0.12+8"
$mavenRoot = Join-Path $runtimeRoot "apache-maven-3.9.11"
$postgresRoot = Join-Path $runtimeRoot "postgresql-17.11"
$mailpitRoot = Join-Path $runtimeRoot "mailpit-1.30.7"

function Get-VerifiedArchive {
    param(
        [Parameter(Mandatory = $true)][string] $Name,
        [Parameter(Mandatory = $true)][string] $Url,
        [Parameter(Mandatory = $true)][string] $Sha256
    )

    $archive = Join-Path $downloadRoot $Name
    if (Test-Path -LiteralPath $archive) {
        $actual = (Get-FileHash -LiteralPath $archive -Algorithm SHA256).Hash
        if ($actual -ne $Sha256) {
            Remove-Item -LiteralPath $archive
        }
    }

    if (-not (Test-Path -LiteralPath $archive)) {
        Write-Host "Downloading $Name ..."
        & curl.exe -fL --retry 3 --output $archive $Url
        if ($LASTEXITCODE -ne 0) {
            throw "Failed to download $Name."
        }
    }

    $actual = (Get-FileHash -LiteralPath $archive -Algorithm SHA256).Hash
    if ($actual -ne $Sha256) {
        throw "$Name checksum verification failed. Expected $Sha256, got $actual."
    }
    return $archive
}

if (-not (Test-Path -LiteralPath (Join-Path $jdkRoot "bin\java.exe"))) {
    if (Test-Path -LiteralPath $jdkRoot) {
        throw "$jdkRoot exists but is incomplete. Remove only that directory and run this script again."
    }
    $archive = Get-VerifiedArchive `
        -Name "temurin-jdk-21.0.12_8.zip" `
        -Url "https://api.adoptium.net/v3/binary/version/jdk-21.0.12%2B8/windows/x64/jdk/hotspot/normal/eclipse" `
        -Sha256 "9BA963EE2371874A74185D18BC7BB2AB9407DF7683300855ED7606E0662321D0"
    Write-Host "Extracting Eclipse Temurin JDK 21 ..."
    Expand-Archive -LiteralPath $archive -DestinationPath $runtimeRoot
}

if (-not (Test-Path -LiteralPath (Join-Path $mavenRoot "bin\mvn.cmd"))) {
    if (Test-Path -LiteralPath $mavenRoot) {
        throw "$mavenRoot exists but is incomplete. Remove only that directory and run this script again."
    }
    $archive = Get-VerifiedArchive `
        -Name "apache-maven-3.9.11-bin.zip" `
        -Url "https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.9.11/apache-maven-3.9.11-bin.zip" `
        -Sha256 "0D7125E8C91097B36EDB990EA5934E6C68B4440EEF4EA96510A0F6815E7EEADB"
    Write-Host "Extracting Apache Maven 3.9.11 ..."
    Expand-Archive -LiteralPath $archive -DestinationPath $runtimeRoot
}

if (-not (Test-Path -LiteralPath (Join-Path $postgresRoot "pgsql\bin\postgres.exe"))) {
    if (Test-Path -LiteralPath $postgresRoot) {
        throw "$postgresRoot exists but is incomplete. Remove only that directory and run this script again."
    }
    $archive = Get-VerifiedArchive `
        -Name "postgresql-17.11-windows-x64-binaries.zip" `
        -Url "https://get.enterprisedb.com/postgresql/postgresql-17.11-1-windows-x64-binaries.zip" `
        -Sha256 "6EABDF00D2893713B75DB4336A23C3FDF505F056E217EC6E2E95D901750CFEA3"
    Write-Host "Extracting PostgreSQL 17.11 (this may take a few minutes) ..."
    New-Item -ItemType Directory -Path $postgresRoot | Out-Null
    Expand-Archive -LiteralPath $archive -DestinationPath $postgresRoot
}

if (-not (Test-Path -LiteralPath (Join-Path $mailpitRoot "mailpit.exe"))) {
    if (Test-Path -LiteralPath $mailpitRoot) {
        throw "$mailpitRoot exists but is incomplete. Remove only that directory and run this script again."
    }
    $archive = Get-VerifiedArchive `
        -Name "mailpit-windows-amd64.zip" `
        -Url "https://github.com/axllent/mailpit/releases/download/v1.30.7/mailpit-windows-amd64.zip" `
        -Sha256 "E4815D2C961A4AD024F4D942D0D286FD7B7DA49C49FC7F01C565FF296BD3FDCE"
    Write-Host "Extracting Mailpit 1.30.7 ..."
    New-Item -ItemType Directory -Path $mailpitRoot | Out-Null
    Expand-Archive -LiteralPath $archive -DestinationPath $mailpitRoot
}

Write-Host ""
Write-Host "Portable local runtime is ready: $runtimeRoot"
Write-Host "Next: .\scripts\local-up.ps1"
