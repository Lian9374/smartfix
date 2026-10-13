param([switch]$ReconcileLegacyMigrations)

Set-Location $PSScriptRoot

$env:DB_PORT = "5433"
docker compose up -d --wait db
if ($LASTEXITCODE -ne 0) {
    throw "Database startup failed. Check Docker Desktop."
}

$env:DB_URL = "jdbc:postgresql://127.0.0.1:5433/smartfix"
$env:DB_USERNAME = "smartfix"
$env:DB_PASSWORD = "smartfix"
$env:SMARTFIX_BOOTSTRAP_ADMIN_ENABLED = "false"

$runOptions = @('spring-boot:run', '-Dspring-boot.run.profiles=dev')
if ($ReconcileLegacyMigrations) {
    $runOptions += '-Dspring-boot.run.arguments=--smartfix.database.reconcile-legacy-migrations=true'
}
mvn @runOptions
if ($LASTEXITCODE -ne 0) { throw 'SmartFix startup failed. Check the first underlying error in the Maven log.' }
