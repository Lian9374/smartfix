[CmdletBinding()]
param([switch]$UseVerifiedJar)

$ErrorActionPreference = 'Stop'
$ProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$Target = Join-Path $ProjectRoot 'target'

Push-Location $ProjectRoot
try {
    if (-not $UseVerifiedJar) {
        & mvn clean verify
        if ($LASTEXITCODE -ne 0) { throw 'Maven verification failed; no deployment ZIP was created.' }
    }

    $Jars = @(Get-ChildItem -LiteralPath $Target -Filter 'smartfix-*.jar' -File)
    if ($Jars.Count -ne 1) { throw 'Expected exactly one verified smartfix-*.jar in target.' }
    $Jar = $Jars[0]
    $UnitReports = @(Get-ChildItem (Join-Path $Target 'surefire-reports') -Filter 'TEST-*.xml' -File)
    if ($UnitReports.Count -eq 0) { throw 'Missing unit test reports. Run mvn clean verify first.' }
    foreach ($Report in $UnitReports) {
        [xml]$Result = Get-Content -LiteralPath $Report.FullName -Raw
        if ([int]$Result.testsuite.failures -gt 0 -or [int]$Result.testsuite.errors -gt 0) {
            throw "Failed tests in $($Report.Name)."
        }
    }
    $SummaryPath = Join-Path $Target 'failsafe-reports\failsafe-summary.xml'
    if (-not (Test-Path -LiteralPath $SummaryPath)) { throw 'Missing integration test summary.' }
    [xml]$Summary = Get-Content -LiteralPath $SummaryPath -Raw
    if ([int]$Summary.'failsafe-summary'.failures -gt 0 -or
        [int]$Summary.'failsafe-summary'.errors -gt 0 -or
        [int]$Summary.'failsafe-summary'.completed -eq 0) {
        throw 'Integration verification is not successful.'
    }
    $Inputs = @(Get-ChildItem (Join-Path $ProjectRoot 'src') -Recurse -File)
    $Inputs += Get-Item (Join-Path $ProjectRoot 'pom.xml')
    if (@($Inputs | Where-Object LastWriteTimeUtc -gt $Jar.LastWriteTimeUtc).Count -gt 0) {
        throw 'Source files changed after the JAR build. Run this script without -UseVerifiedJar.'
    }

    $Label = Get-Date -Format 'yyyyMMdd-HHmmss'
    $OutDir = Join-Path $Target "beanstalk\$Label"
    $Bundle = Join-Path $OutDir 'bundle'
    New-Item -ItemType Directory -Path $Bundle -Force | Out-Null
    foreach ($Item in @('Procfile', 'config', '.platform')) {
        Copy-Item -LiteralPath (Join-Path $PSScriptRoot $Item) -Destination $Bundle -Recurse -Force
    }
    Copy-Item -LiteralPath $Jar.FullName -Destination (Join-Path $Bundle 'app.jar')

    # Configuration updates must enforce the same mount and environment checks.
    $ConfigHooks = Join-Path $Bundle '.platform\confighooks\predeploy'
    New-Item -ItemType Directory -Path $ConfigHooks -Force | Out-Null
    Get-ChildItem (Join-Path $Bundle '.platform\hooks\predeploy') -Filter '*.sh' -File |
        Copy-Item -Destination $ConfigHooks

    # Official public CA certificates only; no AWS credentials are needed.
    $CertDir = Join-Path $Bundle 'certs'
    New-Item -ItemType Directory -Path $CertDir -Force | Out-Null
    $CaPath = Join-Path $CertDir 'rds-global-bundle.pem'
    $CaUrl = 'https://truststore.pki.rds.amazonaws.com/global/global-bundle.pem'
    [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
    Invoke-WebRequest -Uri $CaUrl -OutFile $CaPath -UseBasicParsing
    if ((Get-Content -LiteralPath $CaPath -Raw) -notmatch '-----BEGIN CERTIFICATE-----') {
        throw 'The AWS RDS CA download was not a PEM certificate bundle.'
    }

    # Normalize Linux deployment text and preserve .platform in the ZIP.
    $Utf8 = New-Object System.Text.UTF8Encoding($false)
    Get-ChildItem -LiteralPath $Bundle -Recurse -Force -File | Where-Object {
        $_.Extension -in @('.sh', '.yml', '.conf') -or $_.Name -eq 'Procfile'
    } | ForEach-Object {
        $Text = [IO.File]::ReadAllText($_.FullName).Replace("`r`n", "`n")
        [IO.File]::WriteAllText($_.FullName, $Text, $Utf8)
    }

    $ZipPath = Join-Path $OutDir "smartfix-eb-$Label.zip"
    Add-Type -AssemblyName System.IO.Compression
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $Writer = [IO.Compression.ZipFile]::Open($ZipPath, [IO.Compression.ZipArchiveMode]::Create)
    try {
        Get-ChildItem -LiteralPath $Bundle -Recurse -Force -File | ForEach-Object {
            $EntryName = $_.FullName.Substring($Bundle.Length + 1).Replace('\', '/')
            [IO.Compression.ZipFileExtensions]::CreateEntryFromFile(
                $Writer, $_.FullName, $EntryName, [IO.Compression.CompressionLevel]::Optimal
            ) | Out-Null
        }
    } finally { $Writer.Dispose() }
    $Archive = [IO.Compression.ZipFile]::OpenRead($ZipPath)
    try {
        $Names = @($Archive.Entries | ForEach-Object { $_.FullName })
        foreach ($Required in @('app.jar', 'Procfile', 'config/application-prod.yml',
                'certs/rds-global-bundle.pem', '.platform/hooks/predeploy/10_mount_efs.sh',
                '.platform/confighooks/predeploy/10_mount_efs.sh')) {
            if ($Required -notin $Names) { throw "ZIP is missing $Required" }
        }
    } finally { $Archive.Dispose() }

    [ordered]@{
        jarSha256 = (Get-FileHash -LiteralPath $Jar.FullName -Algorithm SHA256).Hash
        zipSha256 = (Get-FileHash -LiteralPath $ZipPath -Algorithm SHA256).Hash
        gitCommit = (& git rev-parse HEAD)
        trackedChanges = @(& git diff --name-only)
        certificateSource = $CaUrl
        createdAt = (Get-Date).ToString('o')
    } | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $OutDir 'manifest.json') -Encoding UTF8
    Write-Output "Beanstalk ZIP: $ZipPath"
    Write-Output 'Local bundle checks passed. AWS runtime validation is still required.'
} finally { Pop-Location }
