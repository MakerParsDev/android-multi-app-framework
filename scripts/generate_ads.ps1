
$jsonContent = Get-Content -Raw -Path ".ci/apps.json" | ConvertFrom-Json

$testAppId = "ca-app-pub-3940256099942544~3347511713"
$testBanner = "ca-app-pub-3940256099942544/6300978111"
$testInterstitial = "ca-app-pub-3940256099942544/1033173712"
$testNative = "ca-app-pub-3940256099942544/2247696110"
$testRewarded = "ca-app-pub-3940256099942544/5224354917"
$testOpenApp = "ca-app-pub-3940256099942544/9257395921"

foreach ($app in $jsonContent) {
    $flavor = $app.flavor
    $path = "app/src/$flavor/res/values"
    $itemPath = Join-Path $path "ads.xml"
    
    if (!(Test-Path $path)) {
        New-Item -ItemType Directory -Force -Path $path | Out-Null
    }

    $xml = @"
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="admob_app_id" translatable="false">$($app.admob_app_id)</string>
    <string name="ad_unit_banner" translatable="false">$($app.ad_units.banner)</string>
    <string name="ad_unit_interstitial" translatable="false">$($app.ad_units.interstitial)</string>
    <string name="ad_unit_native" translatable="false">$($app.ad_units.native)</string>
    <string name="ad_unit_rewarded" translatable="false">$($app.ad_units.rewarded)</string>
    <string name="ad_unit_open_app" translatable="false">$($app.ad_units.open_app)</string>
</resources>
"@
    Set-Content -Path $itemPath -Value $xml
    Write-Host "Generated $itemPath"
}

# Generate Debug ads.xml (Test Ads)
$debugPath = "app/src/debug/res/values"
if (!(Test-Path $debugPath)) {
    New-Item -ItemType Directory -Force -Path $debugPath | Out-Null
}
$debugXml = @"
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="admob_app_id" translatable="false">$testAppId</string>
    <string name="ad_unit_banner" translatable="false">$testBanner</string>
    <string name="ad_unit_interstitial" translatable="false">$testInterstitial</string>
    <string name="ad_unit_native" translatable="false">$testNative</string>
    <string name="ad_unit_rewarded" translatable="false">$testRewarded</string>
    <string name="ad_unit_open_app" translatable="false">$testOpenApp</string>
</resources>
"@
Set-Content -Path "app/src/debug/res/values/ads.xml" -Value $debugXml
Write-Host "Generated Debug ads.xml"
