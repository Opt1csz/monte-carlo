$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

$ProjectRoot = $PSScriptRoot
$GradleVersion = "9.8.0"
$BootstrapDir = Join-Path $ProjectRoot ".gradle-bootstrap"
$Zip = Join-Path $BootstrapDir "gradle-$GradleVersion-bin.zip"
$GradleHome = Join-Path $BootstrapDir "gradle-$GradleVersion"
$GradleExe = Join-Path $GradleHome "bin\gradle.bat"

New-Item -ItemType Directory -Force -Path $BootstrapDir | Out-Null

# Java must already be installed. The project targets Java 21.
Write-Host "Checking Java..."
java -version

if (-not (Test-Path $GradleExe)) {
    Write-Host "Gradle $GradleVersion not found locally."
    Write-Host "Downloading the official Gradle binary distribution..."

    $Url = "https://services.gradle.org/distributions/gradle-$GradleVersion-bin.zip"
    Invoke-WebRequest -Uri $Url -OutFile $Zip

    Write-Host "Extracting Gradle..."
    Expand-Archive -Path $Zip -DestinationPath $BootstrapDir -Force

    Remove-Item $Zip -Force
}

if (-not (Test-Path $GradleExe)) {
    throw "Gradle bootstrap failed: $GradleExe was not created."
}

Write-Host ""
Write-Host "Building CivMicroscope..."
& $GradleExe clean build

if ($LASTEXITCODE -ne 0) {
    exit $LASTEXITCODE
}

$Jar = Join-Path $ProjectRoot "build\libs\civ-microscope-1.0.0.jar"

Write-Host ""
Write-Host "BUILD SUCCESSFUL" -ForegroundColor Green
Write-Host "Plugin:"
Write-Host $Jar
