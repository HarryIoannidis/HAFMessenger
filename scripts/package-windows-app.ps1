$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

function Fail {
    param([string]$Message)
    [Console]::Error.WriteLine($Message)
    exit 1
}

function Get-EnvValue {
    param(
        [string]$Name,
        [string]$Default
    )
    $value = [Environment]::GetEnvironmentVariable($Name)
    if ([string]::IsNullOrWhiteSpace($value)) {
        return $Default
    }
    return $value
}

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$projectRoot = [System.IO.Path]::GetFullPath((Join-Path $scriptDir ".."))

if (-not [System.Runtime.InteropServices.RuntimeInformation]::IsOSPlatform(
        [System.Runtime.InteropServices.OSPlatform]::Windows)) {
    Fail "This script must be run on Windows."
}

$appName = Get-EnvValue -Name "APP_NAME" -Default "HAFMessenger"
$mainJar = Get-EnvValue -Name "MAIN_JAR" -Default "haf-client.jar"
$mainClass = Get-EnvValue -Name "MAIN_CLASS" -Default "com.haf.client.core.Launcher"
$iconPath = Get-EnvValue -Name "ICON_PATH" -Default (Join-Path $projectRoot "client\src\main\resources\images\logo\app_logo.ico")
$outputDir = Get-EnvValue -Name "OUTPUT_DIR" -Default (Join-Path $projectRoot "client\target\native")
$packageType = (Get-EnvValue -Name "PACKAGE_TYPE" -Default "msi").ToLowerInvariant()
$appVersion = Get-EnvValue -Name "APP_VERSION" -Default "1.0"
$packageWorkDir = Get-EnvValue -Name "PACKAGE_WORK_DIR" -Default (Join-Path $projectRoot "client\target\windows-package")
$mvnw = Get-EnvValue -Name "MVNW" -Default (Join-Path $projectRoot "mvnw.cmd")
$skipTests = (Get-EnvValue -Name "SKIP_TESTS" -Default "true").ToLowerInvariant()

if (-not @("msi", "exe").Contains($packageType)) {
    Fail "Unsupported PACKAGE_TYPE: $packageType`nUse one of: msi, exe"
}

if (-not (Test-Path -LiteralPath $mvnw -PathType Leaf)) {
    Fail "Maven wrapper not found: $mvnw"
}

if (-not (Test-Path -LiteralPath $iconPath -PathType Leaf)) {
    Fail "Icon file not found: $iconPath"
}

if (-not (Get-Command jpackage -ErrorAction SilentlyContinue)) {
    Fail "jpackage is required but was not found in PATH."
}

Write-Host "Building shared and client modules..."
$buildArgs = @("-pl", "shared,client", "-am", "package")
if ($skipTests -eq "true") {
    $buildArgs += "-DskipTests"
}
& $mvnw @buildArgs
if ($LASTEXITCODE -ne 0) {
    exit $LASTEXITCODE
}

$inputDir = Join-Path $packageWorkDir "input"
if (Test-Path -LiteralPath $packageWorkDir) {
    Remove-Item -LiteralPath $packageWorkDir -Recurse -Force
}
New-Item -ItemType Directory -Path $inputDir -Force | Out-Null
New-Item -ItemType Directory -Path $outputDir -Force | Out-Null

$clientJarPath = Join-Path $projectRoot "client\target\$mainJar"
$sharedJarPath = Join-Path $projectRoot "shared\target\haf-shared.jar"

if (-not (Test-Path -LiteralPath $clientJarPath -PathType Leaf)) {
    Fail "Expected client jar not found: $clientJarPath"
}

if (-not (Test-Path -LiteralPath $sharedJarPath -PathType Leaf)) {
    Fail "Expected shared jar not found: $sharedJarPath"
}

Copy-Item -LiteralPath $clientJarPath -Destination (Join-Path $inputDir $mainJar) -Force

Write-Host "Copying runtime dependencies..."
$depArgs = @(
    "-f", (Join-Path $projectRoot "client\pom.xml"),
    "dependency:copy-dependencies",
    "-DincludeScope=runtime",
    "-DoutputDirectory=target/windows-package/input"
)
if ($skipTests -eq "true") {
    $depArgs += "-DskipTests"
}
& $mvnw @depArgs
if ($LASTEXITCODE -ne 0) {
    exit $LASTEXITCODE
}

Get-ChildItem -LiteralPath $inputDir -Filter "shared-*.jar" -File -ErrorAction SilentlyContinue |
    Remove-Item -Force -ErrorAction SilentlyContinue
Copy-Item -LiteralPath $sharedJarPath -Destination (Join-Path $inputDir "shared-1.0-SNAPSHOT.jar") -Force

Get-ChildItem -LiteralPath $outputDir -Filter "$appName*.msi" -File -ErrorAction SilentlyContinue |
    Remove-Item -Force -ErrorAction SilentlyContinue
Get-ChildItem -LiteralPath $outputDir -Filter "$appName*.exe" -File -ErrorAction SilentlyContinue |
    Remove-Item -Force -ErrorAction SilentlyContinue

Write-Host "Packaging $appName ($packageType) with jpackage..."
$jpackageArgs = @(
    "--type", $packageType,
    "--name", $appName,
    "--app-version", $appVersion,
    "--dest", $outputDir,
    "--input", $inputDir,
    "--main-jar", $mainJar,
    "--main-class", $mainClass,
    "--icon", $iconPath,
    "--win-menu",
    "--win-shortcut",
    "--java-options", "--enable-native-access=ALL-UNNAMED"
)
& jpackage @jpackageArgs
if ($LASTEXITCODE -ne 0) {
    exit $LASTEXITCODE
}

$outputFile = Get-ChildItem -LiteralPath $outputDir -Filter "$appName*.$packageType" -File -ErrorAction SilentlyContinue |
    Sort-Object LastWriteTimeUtc -Descending |
    Select-Object -First 1
if (-not $outputFile) {
    Fail "Expected jpackage output not found in $outputDir for type $packageType."
}

Write-Host "Package created:"
Write-Host "  $($outputFile.FullName)"
