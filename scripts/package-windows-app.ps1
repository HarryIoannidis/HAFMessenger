# ============================================================================
# package-windows-app.ps1
#
# Packages the HAFMessenger client as a native Windows installer (.msi or .exe).
#
# What this script produces:
#   1. A compiled fat JAR for the client module
#   2. A self-contained Windows application directory
#   3. A native installer containing the application and private JRE
#
# Usage:
#   .\scripts\package-windows-app.ps1
# ============================================================================

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

function Read-JavaProperties {
    param([string]$Path)

    $properties = @{}
    foreach ($rawLine in (Get-Content -LiteralPath $Path)) {
        $line = $rawLine.Trim()
        if ([string]::IsNullOrWhiteSpace($line) -or $line.StartsWith("#") -or $line.StartsWith("!")) {
            continue
        }

        $separatorIndex = $line.IndexOf("=")
        if ($separatorIndex -lt 0) {
            $separatorIndex = $line.IndexOf(":")
        }
        if ($separatorIndex -lt 0) {
            continue
        }

        $key = $line.Substring(0, $separatorIndex).Trim()
        if ([string]::IsNullOrWhiteSpace($key)) {
            continue
        }

        $value = $line.Substring($separatorIndex + 1).Trim()
        $properties[$key] = $value
    }

    return $properties
}

function Validate-ClientRuntimeConfig {
    param([string]$ConfigPath)

    $properties = Read-JavaProperties -Path $ConfigPath

    function Assert-UriProperty {
        param(
            [string]$Key,
            [string]$ExpectedScheme,
            [bool]$Required
        )

        $rawValue = $null
        if ($properties.ContainsKey($Key)) {
            $rawValue = $properties[$Key]
        }

        if ([string]::IsNullOrWhiteSpace($rawValue)) {
            if ($Required) {
                Fail "Missing required '$Key' in client config: $ConfigPath"
            }
            return $null
        }

        $uri = $null
        if (-not [Uri]::TryCreate($rawValue, [UriKind]::Absolute, [ref]$uri) -or [string]::IsNullOrWhiteSpace($uri.Host)) {
            Fail "Invalid absolute URI for '$Key' in client config: $rawValue"
        }

        if ($uri.Scheme.ToLowerInvariant() -ne $ExpectedScheme) {
            Fail "Invalid scheme for '$Key': expected '$ExpectedScheme', got '$($uri.Scheme)'."
        }

        return $uri
    }

    $serverUri = Assert-UriProperty -Key "server.url.prod" -ExpectedScheme "https" -Required $true
    $wsUri = Assert-UriProperty -Key "server.ws.url.prod" -ExpectedScheme "wss" -Required $true
    $helpUri = Assert-UriProperty -Key "help.center.url.prod" -ExpectedScheme "https" -Required $false

    if ($wsUri -and $wsUri.Query) {
        Fail "Invalid 'server.ws.url.prod': query parameters are not allowed."
    }

    Write-Host "Validated runtime endpoints:"
    Write-Host "  HTTPS: $($serverUri.AbsoluteUri)"
    Write-Host "  WSS:   $($wsUri.AbsoluteUri)"
    if ($helpUri) {
        Write-Host "  Help:  $($helpUri.AbsoluteUri)"
    }
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
$clientConfigPath = Get-EnvValue -Name "CLIENT_CONFIG_PATH" -Default (Join-Path $projectRoot "client\src\main\resources\config\client.properties")
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

if (-not (Test-Path -LiteralPath $clientConfigPath -PathType Leaf)) {
    Fail "Client config file not found: $clientConfigPath"
}

Validate-ClientRuntimeConfig -ConfigPath $clientConfigPath

Write-Host "Building shared and client modules..."
$buildArgs = @(
    "-f", (Join-Path $projectRoot "pom.xml"),
    "-pl", "shared,client",
    "-am", "package"
)
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
    "-DoutputDirectory=$inputDir"
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
