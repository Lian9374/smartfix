$ErrorActionPreference = 'Stop'
$projectPath = 'C:\Users\zhour\smartfix'
if (-not (Test-Path (Join-Path $projectPath 'start-smartfix.ps1'))) {
    throw "Cannot find start-smartfix.ps1 in $projectPath"
}

Push-Location $projectPath
$previousOutOfOrder = [Environment]::GetEnvironmentVariable('SPRING_FLYWAY_OUT_OF_ORDER', 'Process')
$previousDbPort = [Environment]::GetEnvironmentVariable('DB_PORT', 'Process')
try {
    $env:DB_PORT = '5433'
    docker compose up -d --wait db
    if ($LASTEXITCODE -ne 0) { throw 'Database startup failed.' }

    # Query and back up using the actual container credentials, including .env overrides.
    docker compose exec -T db sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "SELECT installed_rank, version, description, success FROM flyway_schema_history ORDER BY installed_rank;"'
    if ($LASTEXITCODE -ne 0) { throw 'Cannot read migration history; no migration was attempted.' }

    $backupName = 'smartfix-before-flyway-' + (Get-Date -Format 'yyyyMMdd-HHmmss') + '.dump'
    $backupDir = Join-Path $projectPath 'local-db-backups'
    New-Item -ItemType Directory -Force -Path $backupDir | Out-Null
    docker compose exec -T db sh -c 'pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Fc -f /tmp/smartfix-before-flyway.dump'
    if ($LASTEXITCODE -ne 0) { throw 'Database backup failed; no migration was attempted.' }
    docker compose cp 'db:/tmp/smartfix-before-flyway.dump' (Join-Path $backupDir $backupName)
    if ($LASTEXITCODE -ne 0) { throw 'Cannot copy database backup; no migration was attempted.' }
    Write-Host "Database backup saved to $backupDir\$backupName. Keep this file private."

    # Applies pending lower-version migrations while retaining Flyway validation.
    # This setting lasts only for this run; restore it when the app exits.
    $env:SPRING_FLYWAY_OUT_OF_ORDER = 'true'
    & (Join-Path $projectPath 'start-smartfix.ps1')
    if ($LASTEXITCODE -ne 0) { throw 'Application startup failed. Inspect the new Flyway error before retrying.' }
}
finally {
    [Environment]::SetEnvironmentVariable('SPRING_FLYWAY_OUT_OF_ORDER', $previousOutOfOrder, 'Process')
    [Environment]::SetEnvironmentVariable('DB_PORT', $previousDbPort, 'Process')
    Pop-Location
}
