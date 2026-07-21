param(
  [Parameter(Mandatory = $true)]
  [string]$Flavor,

  [Parameter(Position = 1)]
  [ValidateSet('major', 'minor', 'patch', 'build')]
  [string]$Type = 'build'
)

$ErrorActionPreference = 'Stop'

$projectRoot = Split-Path -Parent $PSScriptRoot
$versionsFile = Join-Path $projectRoot 'app-versions.properties'

if (-not (Test-Path $versionsFile)) {
  throw "app-versions.properties not found at: $versionsFile"
}

function Get-PropValue([string[]]$lines, [string]$key) {
  $prefix = "$key="
  foreach ($lineRaw in $lines) {
    $line = $lineRaw.Trim()
    if ($line.StartsWith('#') -or $line.Length -eq 0) { continue }
    if ($line.StartsWith($prefix)) { return $line.Substring($prefix.Length).Trim() }
  }
  return $null
}

function Set-PropValue([System.Collections.Generic.List[string]]$lines, [string]$key, [string]$value) {
  $prefix = "$key="
  for ($i = 0; $i -lt $lines.Count; $i++) {
    $trimmed = $lines[$i].Trim()
    if ($trimmed.StartsWith($prefix)) {
      $lines[$i] = "$key=$value"
      return
    }
  }
  $lines.Add("$key=$value")
}

$rawLines = [System.Collections.Generic.List[string]]::new()
(Get-Content -LiteralPath $versionsFile -Encoding UTF8) | ForEach-Object { [void]$rawLines.Add($_) }

$codeKey = "$Flavor.versionCode"
$nameKey = "$Flavor.versionName"

$codeRaw = Get-PropValue $rawLines $codeKey
if ([string]::IsNullOrWhiteSpace($codeRaw)) {
  throw "Missing '$codeKey' in app-versions.properties"
}
if ($codeRaw -notmatch '^\d+$') {
  throw "Invalid numeric value for '$codeKey': '$codeRaw'"
}

$currentCode = [int]$codeRaw
$currentName = Get-PropValue $rawLines $nameKey
if ([string]::IsNullOrWhiteSpace($currentName)) {
  $currentName = '1.0.0'
}

$nameMatch = [regex]::Match($currentName, '^(?<maj>\d+)\.(?<min>\d+)\.(?<pat>\d+)')
if (-not $nameMatch.Success) {
  throw "Invalid semantic version for '$nameKey': '$currentName' (expected x.y.z)"
}

$major = [int]$nameMatch.Groups['maj'].Value
$minor = [int]$nameMatch.Groups['min'].Value
$patch = [int]$nameMatch.Groups['pat'].Value
$nextCode = $currentCode + 1

switch ($Type) {
  'major' {
    $major++
    $minor = 0
    $patch = 0
  }
  'minor' {
    $minor++
    $patch = 0
  }
  'patch' {
    $patch++
  }
  'build' {
    # Keep versionName same for build bump.
  }
}

$nextName =
  if ($Type -eq 'build') { $currentName }
  else { "$major.$minor.$patch" }

Set-PropValue $rawLines $codeKey "$nextCode"
Set-PropValue $rawLines $nameKey "$nextName"

$content = ($rawLines -join "`n") + "`n"
[System.IO.File]::WriteAllText($versionsFile, $content, (New-Object System.Text.UTF8Encoding($false)))

Write-Host ("✓ {0}: versionCode {1} -> {2}, versionName {3}" -f $Flavor, $currentCode, $nextCode, $nextName) -ForegroundColor Green
