Set-Location "C:\Users\zhour\smartfix"

$env:DB_PORT = "5433"
docker compose up -d --wait db
if ($LASTEXITCODE -ne 0) {
    throw "Database startup failed. Check Docker Desktop."
}

$env:DB_URL = "jdbc:postgresql://127.0.0.1:5433/smartfix"
$env:DB_USERNAME = "smartfix"
$env:DB_PASSWORD = "smartfix"
$env:SMARTFIX_BOOTSTRAP_ADMIN_ENABLED = "false"

mvn spring-boot:run "-Dspring-boot.run.profiles=dev"
