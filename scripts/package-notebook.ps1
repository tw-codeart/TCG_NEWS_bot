param([string]$OutputDirectory = (Join-Path $PSScriptRoot '../deployment-output'))
$ErrorActionPreference = 'Stop'
$packageRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..')).Path
$packageJar = Join-Path $packageRoot 'target/tcg-news-bot-0.1.0-SNAPSHOT.jar'
if (-not (Test-Path -LiteralPath $packageJar -PathType Leaf)) {
    throw 'Build the application with mvn test and mvn package before packaging.'
}
$packageFiles = @('Dockerfile', '.dockerignore', 'compose.yaml', '.env.example',
    'deploy/README.md', 'scripts/package-notebook.ps1', 'target/tcg-news-bot-0.1.0-SNAPSHOT.jar')
New-Item -ItemType Directory -Path $OutputDirectory -Force | Out-Null
$packageOutput = (Resolve-Path -LiteralPath $OutputDirectory).Path
$packageName = 'tcg-news-notebook-' + (Get-Date -Format 'yyyyMMdd-HHmmss')
$packageDirectory = Join-Path $packageOutput $packageName
if (Test-Path -LiteralPath $packageDirectory) { throw 'Output package path already exists' }
New-Item -ItemType Directory -Path $packageDirectory | Out-Null
$packageHashes = @{}
foreach ($packageFile in $packageFiles) {
    $packageSource = Join-Path $packageRoot $packageFile
    if (-not (Test-Path -LiteralPath $packageSource -PathType Leaf)) { throw "Missing package input: $packageFile" }
    $packageDestination = Join-Path $packageDirectory $packageFile
    New-Item -ItemType Directory -Path (Split-Path -Parent $packageDestination) -Force | Out-Null
    Copy-Item -LiteralPath $packageSource -Destination $packageDestination
    $packageHashes[$packageFile] = (Get-FileHash -LiteralPath $packageDestination -Algorithm SHA256).Hash
}
$packageHashes | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $packageDirectory 'checksums.json') -Encoding utf8
$packageZip = Join-Path $packageOutput ($packageName + '.zip')
Compress-Archive -LiteralPath $packageDirectory -DestinationPath $packageZip -CompressionLevel Optimal
Write-Output "Deployment bundle: $packageZip"
Write-Output "Bundle SHA256: $((Get-FileHash -LiteralPath $packageZip -Algorithm SHA256).Hash)"
Write-Output 'Only whitelisted runtime files were copied; no .env, database, or account state was included.'
