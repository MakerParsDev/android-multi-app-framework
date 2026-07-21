param(
  [Parameter(Mandatory = $true)]
  [string]$Flavor,

  [Parameter()]
  [ValidateSet('bundle', 'assemble', 'publish')]
  [string]$Action = 'bundle',

  [Parameter()]
  [ValidateSet('none', 'major', 'minor', 'patch', 'build')]
  [string]$Bump = 'none',

  [switch]$LoadFromDotEnv,
  [switch]$NoDaemon,
  [switch]$OverrideFirebaseFromZipBase64,
  [switch]$CreateTag,
  [switch]$PushTag
)

$ErrorActionPreference = 'Stop'

function Write-Section([string]$msg) {
  Write-Host "`n==> $msg" -ForegroundColor Cyan
}

function Load-DotEnv([string]$path) {
  if (-not (Test-Path -LiteralPath $path)) { return }

  foreach ($lineRaw in (Get-Content -LiteralPath $path -Encoding UTF8)) {
    $line = $lineRaw.Trim()
    if ($line.Length -eq 0) { continue }
    if ($line.StartsWith('#')) { continue }
    $parts = $line -split '=', 2
    if ($parts.Count -ne 2) { continue }
    $k = $parts[0].Trim()
    $v = $parts[1].Trim()
    if ($k.Length -eq 0) { continue }
    if ([string]::IsNullOrWhiteSpace($v)) { continue }

    # Do not override existing env vars (allows CI/terminal overrides).
    $existing = (Get-Item -LiteralPath ("env:{0}" -f $k) -ErrorAction SilentlyContinue).Value
    if (-not [string]::IsNullOrWhiteSpace($existing)) { continue }

    Set-Item -Path ("env:{0}" -f $k) -Value $v
  }
}

function Cap-First([string]$s) {
  if ([string]::IsNullOrWhiteSpace($s)) { return $s }
  return ($s.Substring(0, 1).ToUpperInvariant() + $s.Substring(1))
}

function Ensure-File([string]$path, [string]$name) {
  if ([string]::IsNullOrWhiteSpace($path)) { throw "Missing $name" }
  if (-not (Test-Path -LiteralPath $path)) { throw "$name not found: $path" }
}

$projectRoot = Split-Path -Parent $PSScriptRoot
Set-Location -Path $projectRoot

Write-Section "Release script started"
Write-Host "Flavor: $Flavor"
Write-Host "Action: $Action"
Write-Host "Bump:   $Bump"

if ($LoadFromDotEnv) {
  Write-Section "Loading .env"
  Load-DotEnv (Join-Path $projectRoot '.env')
}

$tmpDir = Join-Path $projectRoot 'TEMP_OUT/release_tmp'
$null = New-Item -ItemType Directory -Force -Path $tmpDir

$keystoreTmp = $null
$serviceAccountTmp = $null

try {
  if ($Bump -ne 'none') {
    Write-Section "Bumping app-versions.properties ($Flavor / $Bump)"
    & (Join-Path $projectRoot 'scripts/bump-version.ps1') -Flavor $Flavor -Type $Bump
  }

  # Optional: Firebase override (same idea as CI) when working with secrets.
  if ($OverrideFirebaseFromZipBase64) {
    Write-Section "Optional Firebase override from FIREBASE_CONFIGS_ZIP_BASE64"
    if ([string]::IsNullOrWhiteSpace($env:FIREBASE_CONFIGS_ZIP_BASE64)) {
      throw "Override requested but FIREBASE_CONFIGS_ZIP_BASE64 is empty"
    }

    & python3 (Join-Path $projectRoot 'scripts/ci/materialize_firebase_configs.py') --flavors $Flavor --mode strict
    if ($LASTEXITCODE -ne 0) { throw "Firebase config materialization failed" }
    Write-Host "Firebase config materialized into working tree." -ForegroundColor Yellow
  }

  # Signing: app/build.gradle.kts expects KEYSTORE_FILE path.
  if (-not [string]::IsNullOrWhiteSpace($env:KEYSTORE_FILE) -and (Test-Path -LiteralPath $env:KEYSTORE_FILE)) {
    Write-Section "Signing: using KEYSTORE_FILE"
  } elseif (-not [string]::IsNullOrWhiteSpace($env:KEYSTORE_BASE64)) {
    Write-Section "Signing: decoding KEYSTORE_BASE64 -> KEYSTORE_FILE"
    if ([string]::IsNullOrWhiteSpace($env:KEYSTORE_PASSWORD)) { throw "Missing env var: KEYSTORE_PASSWORD" }
    if ([string]::IsNullOrWhiteSpace($env:KEY_ALIAS)) { throw "Missing env var: KEY_ALIAS" }
    if ([string]::IsNullOrWhiteSpace($env:KEY_PASSWORD)) { throw "Missing env var: KEY_PASSWORD" }
    $keystoreTmp = Join-Path $tmpDir 'release.jks'
    [System.IO.File]::WriteAllBytes($keystoreTmp, [Convert]::FromBase64String($env:KEYSTORE_BASE64))
    $env:KEYSTORE_FILE = $keystoreTmp
  } else {
    Write-Host "⚠️ No signing configured (KEYSTORE_FILE missing and KEYSTORE_BASE64 missing). Release will be unsigned." -ForegroundColor Yellow
  }

  # Play publishing: app/build.gradle.kts expects PLAY_SERVICE_ACCOUNT_JSON as a FILE PATH.
  if ($Action -eq 'publish') {
    Write-Section "Play publishing prerequisites"
    if ([string]::IsNullOrWhiteSpace($env:PLAY_SERVICE_ACCOUNT_JSON)) {
      throw "Missing env var: PLAY_SERVICE_ACCOUNT_JSON (path or json content)"
    }
    if (Test-Path -LiteralPath $env:PLAY_SERVICE_ACCOUNT_JSON) {
      # Path is ok.
      Write-Host "Using service account file: $($env:PLAY_SERVICE_ACCOUNT_JSON)"
    } else {
      # Treat as JSON content and write to temp file.
      $serviceAccountTmp = Join-Path $tmpDir 'service-account.json'
      [System.IO.File]::WriteAllText($serviceAccountTmp, $env:PLAY_SERVICE_ACCOUNT_JSON, (New-Object System.Text.UTF8Encoding($false)))
      $env:PLAY_SERVICE_ACCOUNT_JSON = $serviceAccountTmp
      Write-Host "Service account JSON written to temp file for this run." -ForegroundColor Yellow
    }
  }

  $flavourCap = Cap-First $Flavor
  $task = switch ($Action) {
    'bundle'   { ":app:bundle${flavourCap}Release" }
    'assemble' { ":app:assemble${flavourCap}Release" }
    'publish'  { ":app:publish${flavourCap}ReleaseBundle" }
  }

  $args = @($task, '--stacktrace')
  if ($NoDaemon) { $args += '--no-daemon' }

  Write-Section "Running Gradle: $task"
  & (Join-Path $projectRoot 'gradlew.bat') @args

  # Print common output paths
  Write-Section "Outputs"
  $bundleDir = Join-Path $projectRoot ("app/build/outputs/bundle/{0}Release" -f $Flavor)
  $apkDir = Join-Path $projectRoot ("app/build/outputs/apk/{0}/release" -f $Flavor)
  if (Test-Path $bundleDir) { Write-Host "AAB dir: $bundleDir" }
  if (Test-Path $apkDir) { Write-Host "APK dir: $apkDir" }

  if ($CreateTag) {
    Write-Section "Creating git tag"
    $status = (& git status --porcelain)
    if ($status) { throw "Working tree is not clean; commit/stash before tagging." }

    $versionsFile = Join-Path $projectRoot 'app-versions.properties'
    if (-not (Test-Path -LiteralPath $versionsFile)) {
      throw "Missing app-versions.properties"
    }
    $code = $null
    $name = $null
    foreach ($lineRaw in (Get-Content -LiteralPath $versionsFile -Encoding UTF8)) {
      $line = $lineRaw.Trim()
      if ($line.StartsWith("#") -or $line.Length -eq 0) { continue }
      if ($line.StartsWith("$Flavor.versionCode=")) { $code = $line.Substring(("$Flavor.versionCode=").Length).Trim() }
      if ($line.StartsWith("$Flavor.versionName=")) { $name = $line.Substring(("$Flavor.versionName=").Length).Trim() }
    }
    if ([string]::IsNullOrWhiteSpace($code)) { throw "Missing $Flavor.versionCode in app-versions.properties" }
    if ([string]::IsNullOrWhiteSpace($name)) { $name = "1.0.0" }
    $tag = ("{0}-v{1}+{2}" -f $Flavor, $name, $code)
    & git tag $tag
    Write-Host "Created tag: $tag" -ForegroundColor Green

    if ($PushTag) {
      Write-Section "Pushing tag"
      & git push origin $tag
    }
  }

  Write-Host "`n✓ Release action completed." -ForegroundColor Green
}
finally {
  # Cleanup temp secrets
  if ($keystoreTmp -and (Test-Path -LiteralPath $keystoreTmp)) { Remove-Item -LiteralPath $keystoreTmp -Force }
  if ($serviceAccountTmp -and (Test-Path -LiteralPath $serviceAccountTmp)) { Remove-Item -LiteralPath $serviceAccountTmp -Force }
}
