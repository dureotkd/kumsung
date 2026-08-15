[CmdletBinding()]
param(
    [ValidateSet("demo", "production")]
    [string]$Environment = "demo",

    [ValidateSet("company", "custom", "gmail", "daum", "hanmail", "mailpit")]
    [string]$Provider,

    [string]$Email,
    [string]$HostName,
    [int]$Port,
    [string]$From,
    [string]$QuoteRecipient,
    [string]$SupportRecipient,
    [string]$SupportEmail,

    [ValidateSet("starttls", "ssl", "none")]
    [string]$Security,

    [switch]$NoAuth,
    [switch]$Restart
)

$ErrorActionPreference = "Stop"
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$envFileName = if ($Environment -eq "demo") { ".env.demo" } else { ".env.production" }
$exampleFileName = if ($Environment -eq "demo") { ".env.demo.example" } else { ".env.production.example" }
$composeFileName = if ($Environment -eq "demo") { "compose.demo.yml" } else { "compose.prod.yml" }
$envFile = Join-Path $projectRoot $envFileName
$exampleFile = Join-Path $projectRoot $exampleFileName
$composeFile = Join-Path $projectRoot $composeFileName
$secretDirectory = Join-Path $projectRoot ".secrets"
$secretFile = Join-Path $secretDirectory "mail_password"

function Read-Required([string]$Prompt, [string]$Current) {
    $value = $Current
    if ([string]::IsNullOrWhiteSpace($value)) { $value = Read-Host $Prompt }
    if ([string]::IsNullOrWhiteSpace($value)) { throw "$Prompt is required." }
    return $value.Trim()
}

function ConvertFrom-SecureValue([Security.SecureString]$SecureValue) {
    $pointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($SecureValue)
    try { return [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer) }
    finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer) }
}

function Protect-DotEnvValue([string]$Value) {
    if ($null -eq $Value) { return "''" }
    if ($Value.Contains("`r") -or $Value.Contains("`n") -or $Value.Contains("'")) {
        throw "Environment values cannot contain line breaks or single quotes."
    }
    return "'" + $Value + "'"
}

function Set-DotEnvValue([System.Collections.Generic.List[string]]$Lines, [string]$Key, [string]$Value) {
    $entry = $Key + "=" + (Protect-DotEnvValue $Value)
    $pattern = "^\s*" + [Regex]::Escape($Key) + "="
    for ($index = 0; $index -lt $Lines.Count; $index++) {
        if ($Lines[$index] -match $pattern) { $Lines[$index] = $entry; return }
    }
    $Lines.Add($entry)
}

function Protect-PrivateFile([string]$Path) {
    if ($env:OS -ne "Windows_NT") {
        & chmod 600 $Path
        if ($LASTEXITCODE -ne 0) { throw "Failed to protect $Path." }
        return
    }
    $currentGrant = [Security.Principal.WindowsIdentity]::GetCurrent().Name + ":(F)"
    & icacls $Path "/inheritance:r" | Out-Null
    if ($LASTEXITCODE -ne 0) { throw "Failed to remove inherited permissions from $Path." }
    & icacls $Path "/grant:r" $currentGrant "*S-1-5-18:(F)" "*S-1-5-32-544:(F)" | Out-Null
    if ($LASTEXITCODE -ne 0) { throw "Failed to restrict permissions on $Path." }
}

if ([string]::IsNullOrWhiteSpace($Provider)) {
    $Provider = Read-Required "Provider (company/mailpit)" ""
}
$Provider = $Provider.ToLowerInvariant()
if ($Provider -eq "hanmail") { $Provider = "daum" }
if ($Provider -eq "company") { $Provider = "custom" }

$smtpPassword = ""
$smtpAuth = -not $NoAuth
$smtpStartTls = $false
$smtpStartTlsRequired = $false
$smtpSsl = $false

switch ($Provider) {
    "gmail" {
        $Email = Read-Required "Gmail address" $Email
        $HostName = "smtp.gmail.com"
        if ($Port -le 0) { $Port = 587 }
        if ([string]::IsNullOrWhiteSpace($Security)) { $Security = "starttls" }
        $smtpAuth = $true
        $smtpPassword = ConvertFrom-SecureValue (Read-Host "Google app password" -AsSecureString)
    }
    "daum" {
        $Email = Read-Required "Daum/Hanmail email address" $Email
        $HostName = "smtp.daum.net"
        $Port = 465
        $Security = "ssl"
        $smtpAuth = $true
        $smtpPassword = ConvertFrom-SecureValue (Read-Host "Daum app password" -AsSecureString)
    }
    "custom" {
        $Email = Read-Required "Company-domain SMTP login or email" $Email
        $HostName = Read-Required "SMTP host supplied by the mail provider" $HostName
        if ($Port -le 0) { $Port = 587 }
        if ([string]::IsNullOrWhiteSpace($Security)) { $Security = "starttls" }
        if ($smtpAuth) {
            $smtpPassword = ConvertFrom-SecureValue (Read-Host "Company SMTP password or app password" -AsSecureString)
        }
    }
    "mailpit" {
        if ($Environment -ne "demo") { throw "Mailpit is allowed only in the demo environment." }
        $Email = "demo"
        $HostName = "mailpit"
        $Port = 1025
        $Security = "none"
        $smtpAuth = $false
        if ([string]::IsNullOrWhiteSpace($From)) { $From = "no-reply@kumsungenc.co.kr" }
    }
}

if ($smtpAuth -and [string]::IsNullOrWhiteSpace($smtpPassword)) {
    throw "An SMTP password is required when authentication is enabled."
}

switch ($Security) {
    "starttls" { $smtpStartTls = $true; $smtpStartTlsRequired = $true }
    "ssl" { $smtpSsl = $true; if ($Port -le 0 -or $Port -eq 587) { $Port = 465 } }
}

if ([string]::IsNullOrWhiteSpace($From)) { $From = $Email }
if ([string]::IsNullOrWhiteSpace($QuoteRecipient)) { $QuoteRecipient = $From }
if ([string]::IsNullOrWhiteSpace($SupportRecipient)) { $SupportRecipient = $From }
if ([string]::IsNullOrWhiteSpace($SupportEmail)) { $SupportEmail = $SupportRecipient }
if (-not (Test-Path -LiteralPath $envFile)) { Copy-Item -LiteralPath $exampleFile -Destination $envFile }
if (-not (Test-Path -LiteralPath $secretDirectory)) { New-Item -ItemType Directory -Path $secretDirectory | Out-Null }

$lines = [System.Collections.Generic.List[string]]::new()
[IO.File]::ReadAllLines($envFile) | ForEach-Object { $lines.Add($_) }
Set-DotEnvValue $lines "MAIL_PROVIDER" $Provider.ToUpperInvariant()
Set-DotEnvValue $lines "MAIL_HOST" $HostName
Set-DotEnvValue $lines "MAIL_PORT" $Port.ToString()
Set-DotEnvValue $lines "MAIL_USERNAME" $Email
Set-DotEnvValue $lines "MAIL_PASSWORD" ""
Set-DotEnvValue $lines "MAIL_PASSWORD_FILE" "/run/secrets/mail_password"
Set-DotEnvValue $lines "MAIL_PASSWORD_SECRET_FILE" "./.secrets/mail_password"
Set-DotEnvValue $lines "MAIL_FROM" $From
Set-DotEnvValue $lines "MAIL_AUTH" $smtpAuth.ToString().ToLowerInvariant()
Set-DotEnvValue $lines "MAIL_STARTTLS" $smtpStartTls.ToString().ToLowerInvariant()
Set-DotEnvValue $lines "MAIL_STARTTLS_REQUIRED" $smtpStartTlsRequired.ToString().ToLowerInvariant()
Set-DotEnvValue $lines "MAIL_SSL" $smtpSsl.ToString().ToLowerInvariant()
Set-DotEnvValue $lines "QUOTE_RECIPIENT" $QuoteRecipient
Set-DotEnvValue $lines "SUPPORT_RECIPIENT" $SupportRecipient
Set-DotEnvValue $lines "SUPPORT_EMAIL" $SupportEmail

$tempFile = $envFile + ".tmp"
[IO.File]::WriteAllLines($tempFile, $lines, [Text.UTF8Encoding]::new($false))
[IO.File]::WriteAllText($secretFile, $smtpPassword, [Text.UTF8Encoding]::new($false))
Move-Item -LiteralPath $tempFile -Destination $envFile -Force
$smtpPassword = $null
Protect-PrivateFile $envFile
Protect-PrivateFile $secretFile

docker compose --env-file $envFile -f $composeFile config --quiet
if ($LASTEXITCODE -ne 0) { throw "Docker Compose configuration validation failed." }

Write-Host ""
Write-Host "Saved SMTP settings: $envFileName"
Write-Host "Provider: $($Provider.ToUpperInvariant())"
Write-Host "Server: $HostName`:$Port"
Write-Host "From: $From"
Write-Host "Quote recipient: $QuoteRecipient"
Write-Host "Support recipient: $SupportRecipient"
Write-Host "Support public/reply address: $SupportEmail"
Write-Host "The password is stored in the protected .secrets/mail_password file, not in .env."

if ($Restart) {
    docker compose --env-file $envFile -f $composeFile up -d --build --force-recreate
    if ($LASTEXITCODE -ne 0) { throw "Failed to restart the containers." }
    Write-Host "Restarted the containers with the new SMTP settings."
} else {
    Write-Host "Run the same command with -Restart, or restart the environment to apply the settings."
}
