$ErrorActionPreference = "Stop"
$projectDir = Split-Path -Parent $PSScriptRoot
$envFile = Join-Path $projectDir ".env.demo"
$composeFile = Join-Path $projectDir "compose.demo.yml"
$dockerDesktop = "C:\Program Files\Docker\Docker\Docker Desktop.exe"

function Test-DockerEngine {
    $previousPreference = $ErrorActionPreference
    try {
        $ErrorActionPreference = "SilentlyContinue"
        & docker info --format "{{.ServerVersion}}" *> $null
        return $LASTEXITCODE -eq 0
    }
    finally {
        $ErrorActionPreference = $previousPreference
    }
}

function Invoke-Docker {
    param(
        [Parameter(Mandatory = $true)]
        [string[]] $DockerArguments,
        [Parameter(Mandatory = $true)]
        [string] $FailureMessage
    )

    $previousPreference = $ErrorActionPreference
    try {
        # Windows PowerShell 5 can convert native stderr output into ErrorRecords.
        $ErrorActionPreference = "Continue"
        & docker @DockerArguments
        $exitCode = $LASTEXITCODE
    }
    finally {
        $ErrorActionPreference = $previousPreference
    }

    if ($exitCode -ne 0) {
        throw $FailureMessage
    }
}

function Protect-PrivateFile([string] $Path) {
    $currentGrant = [Security.Principal.WindowsIdentity]::GetCurrent().Name + ":(F)"
    & icacls $Path "/inheritance:r" | Out-Null
    if ($LASTEXITCODE -ne 0) { throw "$Path 상속 권한 제거에 실패했습니다." }
    & icacls $Path "/grant:r" $currentGrant "*S-1-5-18:(F)" "*S-1-5-32-544:(F)" | Out-Null
    if ($LASTEXITCODE -ne 0) { throw "$Path 개인 권한 설정에 실패했습니다." }
}

if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
    throw "Docker CLI was not found. Install Docker Desktop first."
}

if (-not (Test-Path -LiteralPath $envFile)) {
    throw ".env.demo was not found. Copy .env.demo.example and enter the required values."
}

# ngrok 토큰, DB/관리자 자격증명 등이 든 파일을 현재 사용자와 시스템 관리자만 읽게 한다.
Protect-PrivateFile $envFile

$demoValues = @{}
Get-Content -LiteralPath $envFile | ForEach-Object {
    if ($_ -match '^\s*([^#][^=]*)=(.*)$') {
        $demoValues[$matches[1].Trim()] = $matches[2].Trim()
    }
}

$invalidSettings = @()
foreach ($requiredName in @("NGROK_AUTHTOKEN", "NGROK_DOMAIN", "DB_PASSWORD", "ADMIN_EMAIL", "ADMIN_PASSWORD")) {
    $requiredValue = [string] $demoValues[$requiredName]
    if ([string]::IsNullOrWhiteSpace($requiredValue) -or
        $requiredValue -match 'replace-with' -or
        $requiredValue -match 'example\.com') {
        $invalidSettings += $requiredName
    }
}

if ([string] $demoValues["ADMIN_EMAIL"] -notmatch '^[^@\s]+@[^@\s]+\.[^@\s]+$') {
    $invalidSettings += "ADMIN_EMAIL(valid email required)"
}

if (([string] $demoValues["ADMIN_PASSWORD"]).Length -lt 12) {
    $invalidSettings += "ADMIN_PASSWORD(minimum 12 characters)"
}

if ($invalidSettings.Count -gt 0) {
    throw "Fix these values in .env.demo: $($invalidSettings -join ', ')"
}

if (-not (Test-DockerEngine)) {
    if (-not (Test-Path -LiteralPath $dockerDesktop)) {
        throw "The Docker engine is unavailable and Docker Desktop was not found."
    }

    Write-Host "Starting Docker Desktop and waiting for the Linux engine..."
    Start-Process -FilePath $dockerDesktop -WindowStyle Hidden

    $ready = $false
    for ($attempt = 1; $attempt -le 60; $attempt++) {
        Start-Sleep -Seconds 3
        if (Test-DockerEngine) {
            $ready = $true
            break
        }
        if ($attempt % 5 -eq 0) {
            Write-Host "  Waiting for Docker... $($attempt * 3)s"
        }
    }

    if (-not $ready) {
        throw "Docker Desktop was not ready in 3 minutes. Check Docker Desktop for a WSL or engine error."
    }
}

Write-Host "Docker engine is ready."

Invoke-Docker `
    -DockerArguments @("compose", "--env-file", $envFile, "-f", $composeFile, "config", "-q") `
    -FailureMessage ".env.demo or compose.demo.yml is invalid."

Invoke-Docker `
    -DockerArguments @("compose", "--env-file", $envFile, "-f", $composeFile, "up", "-d", "--build") `
    -FailureMessage "Failed to start the demo environment. Check the Docker logs."

$domainLine = Get-Content -LiteralPath $envFile |
    Where-Object { $_ -match '^\s*NGROK_DOMAIN\s*=' } |
    Select-Object -First 1
$domain = ($domainLine -split '=', 2)[1].Trim()

Write-Host ""
Write-Host "Public demo:    https://$domain"
Write-Host "Local Mailpit:  http://localhost:8025"
Write-Host "ngrok inspector: http://localhost:4040"
Write-Host "Local preview:  http://localhost:8088"
Write-Host ""
Write-Host "Status: docker compose --env-file .env.demo -f compose.demo.yml ps"
Write-Host "Stop:   .\scripts\demo-down.ps1"
