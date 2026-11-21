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
if ($LASTEXITCODE -ne 0) {
    throw "Could not inspect installed target package $PackageName."
}
$inspectorComponent = $PackageName + '/com.parsfilo.contentapp.debug.AdInspectorDebugActivity'
$resolvedComponent = & $adb -s $DeviceSerial shell cmd package resolve-activity --brief -n $inspectorComponent 2>&1
if ($LASTEXITCODE -ne 0 -or -not (($resolvedComponent -join [Environment]::NewLine).Contains($inspectorComponent))) {
    throw "The installed $PackageName build does not expose the protected Ad Inspector activity. Update the Internal app first."
}
$packageDetailsText = $packageDetails -join [Environment]::NewLine
if ($packageDetailsText -notmatch 'installerPackageName=com\.android\.vending') {
    throw 'Target app is not Play-installed; refusing to run this Play-build diagnostic.'
}

$shellPackage = & $adb -s $DeviceSerial shell dumpsys package com.android.shell 2>&1
$shellPackageText = $shellPackage -join [Environment]::NewLine
if ($shellPackageText -notmatch 'android\.permission\.DUMP: granted=true') {
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

Write-Output "Target package: $PackageName"
Write-Output "Device model: $model"
Write-Output 'Starting the existing app through its normal UMP/ads initialization path.'
# Android 9 can block am start -W after the activity is already visible.
# Dispatch normally; the later foreground and Inspector probes provide evidence.
# Windows PowerShell 5.1 promotes native stderr warnings (such as a reused
# foreground task) to terminating NativeCommandError under Stop.
# Temporarily capture the CLI result, restoring strict error handling immediately.
$previousErrorActionPreference = $ErrorActionPreference
try {
    $ErrorActionPreference = 'Continue'
    $mainStart = & $adb -s $DeviceSerial shell am start -n $mainComponent 2>&1
    $mainStartExitCode = $LASTEXITCODE
} finally {
    $ErrorActionPreference = $previousErrorActionPreference
}
$mainStart | ForEach-Object { Write-Output $_ }
if ($mainStartExitCode -ne 0 -or ($mainStart -join [Environment]::NewLine) -match 'Error:|Permission Denial') {
    throw 'Could not start the existing MainActivity.'
}

# A normal app-open ad can cover the Inspector even when the SDK has launched it.
# Give startup time to settle, then dismiss only an identified full-screen ad.
Start-Sleep -Seconds 3
$launchForeground = @(
    & $adb -s $DeviceSerial shell dumpsys activity activities |
        Select-String 'mResumedActivity|topResumedActivity' |
        ForEach-Object { $_.Line.Trim() }
)
if (($launchForeground -join ' ') -match 'com\.google\.android\.gms\.ads') {
    $launchUiPath = '/sdcard/ad-inspector-337-launch.xml'
    try {
        & $adb -s $DeviceSerial shell uiautomator dump $launchUiPath | Out-Null
        if ($LASTEXITCODE -eq 0) {
            $launchUi = & $adb -s $DeviceSerial shell cat $launchUiPath
            $launchText = $launchUi -join [Environment]::NewLine
            if ($launchText -match 'Uygulamaya d?n|Return to app' -and $launchText -notmatch 'Ad Inspector') {
                Write-Output 'Closing the normal full-screen app-open ad before Inspector diagnostics.'
                & $adb -s $DeviceSerial shell input keyevent KEYCODE_BACK | Out-Null
                if ($LASTEXITCODE -ne 0) {
                    throw 'Could not close the full-screen ad before Inspector diagnostics.'
                }
                Start-Sleep -Seconds 2
            }
        }
    } finally {
        & $adb -s $DeviceSerial shell rm -f $launchUiPath | Out-Null
    }
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

# Release R8 can remove informational Log.i entries; observable SDK UI is the success criterion.
# Keep error/consent gating, but do not require informational logcat markers.
$uiVisible = $false
$opaqueWebViewSamples = 0
$uiDumpFailures = 0
$deadline = [DateTimeOffset]::UtcNow.AddSeconds(90)
$foreground = @()
$hierarchyPath = '/sdcard/ad-inspector-337-ui.xml'
try {
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
            throw 'Ad Inspector was blocked or returned an SDK error. Consent was not reset or bypassed.'
        }

        $foreground = @(
            & $adb -s $DeviceSerial shell dumpsys activity activities |
                Select-String 'mResumedActivity|topResumedActivity' |
                ForEach-Object { $_.Line.Trim() }
        )
        if (($foreground -join ' ') -match 'com\.google\.android\.gms\.ads') {
            # Android 9's "uiautomator dump /dev/tty" reports a path, not the XML.
            $dumpResult = & $adb -s $DeviceSerial shell uiautomator dump $hierarchyPath 2>&1
            if ($LASTEXITCODE -eq 0) {
                $hierarchy = & $adb -s $DeviceSerial shell cat $hierarchyPath 2>&1
                if ($LASTEXITCODE -eq 0) {
                    $uiText = $hierarchy -join [Environment]::NewLine
                    if ($uiText -match 'android.webkit.WebView') {
                        # Android 9 can expose the Inspector as an opaque WebView.
                        # A normal app-open ad can also occupy Google Ads AdActivity.
                        $opaqueWebViewSamples++
                    }
                    if ($uiText -match 'Ad Inspector' -and $uiText -match 'Ad units|Adapters|Privacy') {
                        $uiVisible = $true
                        break
                    }
                } else {
                    $uiDumpFailures++
                }
            } else {
                # Android 9 can kill uiautomator (exit 137) without producing XML.
                $uiDumpFailures++
            }
            if ($opaqueWebViewSamples -ge 5 -or $uiDumpFailures -ge 3) {
                # Bounded unreadable probes cannot establish UI success or SDK failure.
                break
            }
        }
        Start-Sleep -Seconds 2
    }
} finally {
    # Only remove our own temporary UI hierarchy; never touch app storage.
    & $adb -s $DeviceSerial shell rm -f $hierarchyPath | Out-Null
}
$after = Get-TargetPackageFingerprint
if ($before -ne $after) {
    throw "Target app metadata changed unexpectedly. Before: $before After: $after"
}

if (-not $uiVisible) {
    if (($opaqueWebViewSamples -gt 0 -or $uiDumpFailures -gt 0) -and
        ($foreground -join ' ') -match 'com\.google\.android\.gms\.ads') {
        # Neither AdActivity nor an unreadable/failed XML dump proves Inspector UI.
        Write-Warning 'No successful Ad Inspector UI verification can be inferred from an opaque WebView or failed Android UIAutomator dump. Review an independent device screenshot for the Inspector title and tabs; do not tap advertisements.'
        Write-Output "AD_INSPECTOR_VISUAL_REVIEW_REQUIRED package=$PackageName"
        Write-Output "TARGET_APP_UNCHANGED package=$PackageName"
        Write-Output "AD_INSPECTOR_AUTOMATION_INCONCLUSIVE package=$PackageName"
        $foreground | ForEach-Object { Write-Output $_ }
        exit 2
    }
    throw 'Ad Inspector UI could not be confirmed: no readable Inspector title and tabs were observed. Consent was not bypassed.'
}

Write-Output 'AD_INSPECTOR_UI_VISIBLE'
Write-Output "TARGET_APP_UNCHANGED package=$PackageName"
Write-Output "AD_INSPECTOR_AUTOMATION_PASSED package=$PackageName"
$foreground | ForEach-Object { Write-Output $_ }
Write-Output 'The Inspector remains open; close it normally when finished.'
