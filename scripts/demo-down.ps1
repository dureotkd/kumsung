$ErrorActionPreference = "Stop"
$projectDir = Split-Path -Parent $PSScriptRoot
$envFile = Join-Path $projectDir ".env.demo"
$composeFile = Join-Path $projectDir "compose.demo.yml"

docker compose --env-file $envFile -f $composeFile down
if ($LASTEXITCODE -ne 0) {
    throw "Failed to stop the demo environment."
}

Write-Host "Demo environment stopped. Database and upload volumes were preserved."
