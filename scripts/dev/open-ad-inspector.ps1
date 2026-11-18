<#
.SYNOPSIS
Opens Google Mobile Ads Ad Inspector in an existing Play-installed test app.

.DESCRIPTION
Restricted to the registered Samsung SM-G611F and the two #337 target apps.
The script never installs/replaces the target app, clears app data, or resets UMP consent.

.EXAMPLE
.\scripts\dev\open-ad-inspector.ps1 -PackageName com.parsfilo.namazsurelerivedualarsesli -DeviceSerial <adb-serial>
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [ValidateSet(
        'com.parsfilo.namazsurelerivedualarsesli',
        'com.parsfilo.ismiazamduasi'
    )]
    [string]$PackageName,

    [Parameter(Mandatory = $true)]
    [ValidateNotNullOrEmpty()]
    [string]$DeviceSerial
)

$ErrorActionPreference = 'Stop'

if ([string]::IsNullOrWhiteSpace($env:ANDROID_HOME)) {
    $env:ANDROID_HOME = $env:ANDROID_SDK_ROOT
}
if ([string]::IsNullOrWhiteSpace($env:ANDROID_HOME)) {
    $env:ANDROID_HOME = Join-Path $env:LOCALAPPDATA 'Android\Sdk'
}

$adb = (Get-Command adb -ErrorAction SilentlyContinue).Source
if ([string]::IsNullOrWhiteSpace($adb)) {
    $adb = Join-Path $env:ANDROID_HOME 'platform-tools\adb.exe'
}
if (-not (Test-Path $adb)) {
    throw 'ADB was not found. Set ANDROID_HOME or add adb to PATH.'
}

$model = (& $adb -s $DeviceSerial shell getprop ro.product.model 2>&1 | Out-String).Trim()
if ($LASTEXITCODE -ne 0 -or $model -ne 'SM-G611F') {
    throw "Unsupported diagnostic device model: $model. No app action was performed."
}

$targetInstall = & $adb -s $DeviceSerial shell pm path $PackageName 2>&1
if ($LASTEXITCODE -ne 0 -or -not ($targetInstall -match '^package:')) {
    throw "Target app $PackageName is not already installed. The script never installs or replaces the target app."
}

$packageDetails = & $adb -s $DeviceSerial shell dumpsys package $PackageName 2>&1
if ($LASTEXITCODE -ne 0 -or $packageDetails -notmatch 'com\.parsfilo\.contentapp\.debug\.AdInspectorDebugActivity') {
    throw "The installed $PackageName build does not contain the ADB-only Ad Inspector entry point. Update the Internal app first."
}
if ($packageDetails -notmatch 'installerPackageName=com\.android\.vending') {
    throw 'Target app is not Play-installed; refusing to run this Play-build diagnostic.'
}

$shellPackage = & $adb -s $DeviceSerial shell dumpsys package com.android.shell 2>&1
if ($shellPackage -notmatch 'android\.permission\.DUMP: granted=true') {
    throw 'ADB shell does not hold android.permission.DUMP on this device.'
}

function Get-TargetPackageFingerprint {
    $details = & $adb -s $DeviceSerial shell dumpsys package $PackageName 2>&1
    if ($LASTEXITCODE -ne 0) {
        throw "Could not inspect installed target package $PackageName."
    }

    $detailText = $details -join [Environment]::NewLine
    $requiredFields = @(
        'versionCode=',
        'firstInstallTime=',
        'lastUpdateTime=',
        'installerPackageName='
    )
    foreach ($field in $requiredFields) {
        if ($detailText -notmatch [regex]::Escape($field)) {
            throw "Installed package metadata is missing $field for $PackageName."
        }
    }

    $lines = @(
        $details |
            Select-String 'versionCode=|firstInstallTime=|lastUpdateTime=|installerPackageName=' |
            ForEach-Object { $_.Line.Trim() }
    )
    return $lines -join [Environment]::NewLine
}

$before = Get-TargetPackageFingerprint
$action = 'com.parsfilo.contentapp.action.OPEN_AD_INSPECTOR'
$mainComponent = $PackageName + '/com.parsfilo.contentapp.MainActivity'
$inspectorComponent = $PackageName + '/com.parsfilo.contentapp.debug.AdInspectorDebugActivity'

Write-Output "Target package: $PackageName"
Write-Output "Device model: $model"
Write-Output 'Starting the existing app through its normal UMP/ads initialization path.'
$mainStart = & $adb -s $DeviceSerial shell am start -W -n $mainComponent 2>&1
$mainStart | ForEach-Object { Write-Output $_ }
if ($LASTEXITCODE -ne 0 -or ($mainStart -join [Environment]::NewLine) -match 'Error:|Permission Denial') {
    throw 'Could not start the existing MainActivity.'
}

Write-Output 'Calling the protected in-app Ad Inspector API entry point (no shake, no install).'
$startEpochText = (& $adb -s $DeviceSerial shell date +%s 2>&1 | Out-String).Trim()
if ($LASTEXITCODE -ne 0 -or $startEpochText -notmatch '^\d+$') {
    throw 'Could not establish the device log time boundary.'
}
$startEpoch = [double]$startEpochText
$inspectorStart = & $adb -s $DeviceSerial shell am start -W -n $inspectorComponent -a $action 2>&1
$inspectorStart | ForEach-Object { Write-Output $_ }
if ($LASTEXITCODE -ne 0 -or ($inspectorStart -join [Environment]::NewLine) -match 'Error:|Permission Denial') {
    throw 'ADB could not start the DUMP-protected Ad Inspector entry point.'
}

$openRequested = $false
$deadline = [DateTimeOffset]::UtcNow.AddSeconds(70)
while ([DateTimeOffset]::UtcNow -lt $deadline) {
    $logs = & $adb -s $DeviceSerial shell logcat -d -v epoch -s AdInspectorDebug 2>&1
    $recentLogs = @(
        foreach ($line in $logs) {
            if ($line -match '^\s*(?<epoch>\d+(?:\.\d+)?)' -and [double]$Matches.epoch -ge $startEpoch) {
                $line
            }
        }
    )
    $recentText = $recentLogs -join [Environment]::NewLine

    if ($recentText -match 'AD_INSPECTOR_BLOCKED|AD_INSPECTOR_ERROR') {
        throw "Ad Inspector was blocked or returned an SDK error: $recentText"
    }
    if ($recentText -match 'AD_INSPECTOR_OPEN_REQUESTED') {
        $openRequested = $true
        break
    }
    Start-Sleep -Seconds 1
}
if (-not $openRequested) {
    throw 'Ad Inspector did not reach SDK-ready state within 70 seconds. UMP consent was not reset or bypassed.'
}

$uiVisible = $false
$uiDeadline = [DateTimeOffset]::UtcNow.AddSeconds(30)
$foreground = @()
while ([DateTimeOffset]::UtcNow -lt $uiDeadline) {
    $hierarchy = & $adb -s $DeviceSerial shell uiautomator dump /dev/tty 2>&1
    if (($hierarchy -join [Environment]::NewLine) -match 'Ad Inspector|Reklam inceleyici') {
        $uiVisible = $true
        break
    }

    $foreground = @(
        & $adb -s $DeviceSerial shell dumpsys activity activities |
            Select-String 'mResumedActivity|topResumedActivity' |
            ForEach-Object { $_.Line.Trim() }
    )
    if (($foreground -join ' ') -match 'AdInspector|com\.google\.android\.gms\.ads') {
        $uiVisible = $true
        break
    }
    Start-Sleep -Seconds 2
}
if (-not $uiVisible) {
    throw "The SDK API was called, but the Ad Inspector UI could not be confirmed. Foreground: $($foreground -join ' | ')"
}

$after = Get-TargetPackageFingerprint
if ($before -ne $after) {
    throw "Target app metadata changed unexpectedly. Before: $before After: $after"
}

Write-Output 'AD_INSPECTOR_UI_VISIBLE'
Write-Output "TARGET_APP_UNCHANGED package=$PackageName"
Write-Output "AD_INSPECTOR_AUTOMATION_PASSED package=$PackageName"
$foreground | ForEach-Object { Write-Output $_ }
Write-Output 'The Inspector remains open; close it normally when finished.'
