$ErrorActionPreference = "Stop"

Write-Host "Checking Java..."
java -version

if (-not (Get-Command gradle -ErrorAction SilentlyContinue)) {
    Write-Host ""
    Write-Host "Gradle was not found."
    Write-Host "Install Gradle 8.10+ and make sure 'gradle' is on PATH."
    Write-Host "Then run this script again."
    exit 1
}

Write-Host ""
Write-Host "Building CivMicroscope..."
gradle clean build

$jar = Join-Path $PSScriptRoot "build\libs\civ-microscope-1.0.0.jar"
if (Test-Path $jar) {
    Write-Host ""
    Write-Host "BUILT:"
    Write-Host $jar
} else {
    throw "Build completed but expected plugin jar was not found."
}
