Param(
  [Parameter(Mandatory = $true)]
  [string]$PackageName,

  [ValidateSet("enable", "disable")]
  [string]$Mode = "enable"
)

$ErrorActionPreference = "Stop"

function Assert-Adb {
  $adb = Get-Command adb -ErrorAction SilentlyContinue
  if (-not $adb) {
    throw "adb not found in PATH. Android SDK platform-tools yuklu olmali."
  }
}

Assert-Adb

if ($Mode -eq "enable") {
  Write-Host "Enabling Firebase Analytics DebugView for package: $PackageName"
  adb shell setprop debug.firebase.analytics.app $PackageName | Out-Null
  adb shell am force-stop $PackageName | Out-Null
  Write-Host "OK. Uygulamayi ac ve Firebase Console -> DebugView ekranindan eventleri kontrol et."
  exit 0
}

Write-Host "Disabling Firebase Analytics DebugView"
adb shell setprop debug.firebase.analytics.app .none. | Out-Null
Write-Host "OK."

