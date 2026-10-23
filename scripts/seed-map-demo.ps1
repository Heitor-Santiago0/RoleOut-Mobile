# Adds repeatable, clearly fictional map fixtures ONLY to the isolated local stack.
# Usage: powershell -File scripts/seed-map-demo.ps1
# Does not modify existing integration fixtures or user records.
$ErrorActionPreference = 'Stop'
$compose = Join-Path $PSScriptRoot '../compose.integration.yml'
$container = docker compose -p roleout-integration -f $compose ps -q mysql
if ($LASTEXITCODE -ne 0 -or -not $container) { throw 'Start the local integration database first.' }
$inspection = docker inspect $container | ConvertFrom-Json
if ($LASTEXITCODE -ne 0 -or $inspection[0].Config.Labels.'com.docker.compose.project' -ne 'roleout-integration') { throw 'Refusing to seed a different Docker project.' }
$previousEncoding = $OutputEncoding
try {
    $OutputEncoding = [System.Text.UTF8Encoding]::new($false)
    Get-Content (Join-Path $PSScriptRoot 'seed-map-demo.sql') -Raw -Encoding UTF8 |
        docker compose -p roleout-integration -f $compose exec -T mysql mysql --default-character-set=utf8mb4 -uroleout -pintegration-local-only -D roleout
    if ($LASTEXITCODE -ne 0) { throw 'Demo map seed failed.' }
} finally { $OutputEncoding = $previousEncoding }
