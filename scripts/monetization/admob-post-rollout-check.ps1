param(
    [string]$TokenFile = "SECRET/ADMOB_KOTNROL/token.json",
    [string]$Publisher = "",
    [double]$OverallShowRateBaseline = 38.17,
    [double]$LatestShowRateBaseline = 36.21,
    [double]$OverallTargetDelta = 4.0,
    [double]$LatestTargetDelta = 3.0
)

$ErrorActionPreference = "Stop"

function Get-JsonValue {
    param(
        [Parameter(Mandatory = $true)] $Object,
        [Parameter(Mandatory = $true)] [string]$Path
    )

    $current = $Object
    foreach ($segment in $Path.Split(".")) {
        if ($null -eq $current) { return $null }
        $current = $current.$segment
    }
    return $current
}

function Format-Percent {
    param([double]$Value)
    return ("{0:N2}%" -f $Value)
}

function Format-PercentFromFraction {
    param([double]$Value)
    return (Format-Percent ($Value * 100))
}

function Write-Section {
    param([string]$Title)
    Write-Host ""
    Write-Host "=== $Title ===" -ForegroundColor Cyan
}

$root = Resolve-Path (Join-Path $PSScriptRoot "..\..")
$resolvedTokenFile = Join-Path $root $TokenFile

if (-not (Test-Path $resolvedTokenFile)) {
    throw "Token file not found: $resolvedTokenFile"
}

$todayOut = Join-Path $root "TEMP_OUT\admob_today_report.json"
$latestOut = Join-Path $root "TEMP_OUT\admob_today_latest_report.json"

$todayArgs = @(
    (Join-Path $root "scripts\ci\check_admob_today.py"),
    "--token-file", $resolvedTokenFile,
    "--out-json", $todayOut
)
if ($Publisher) {
    $todayArgs += @("--publisher", $Publisher)
}

$latestArgs = @(
    (Join-Path $root "scripts\ci\check_admob_today_latest.py"),
    "--token-file", $resolvedTokenFile,
    "--out-json", $latestOut
)
if ($Publisher) {
    $latestArgs += @("--publisher", $Publisher)
}

Write-Host "Running AdMob today report..." -ForegroundColor Yellow
& python @todayArgs
if ($LASTEXITCODE -ne 0) {
    throw "check_admob_today.py failed with exit code $LASTEXITCODE"
}

Write-Host "Running AdMob today latest-version report..." -ForegroundColor Yellow
& python @latestArgs
if ($LASTEXITCODE -ne 0) {
    throw "check_admob_today_latest.py failed with exit code $LASTEXITCODE"
}

$today = Get-Content $todayOut -Raw | ConvertFrom-Json
$latest = Get-Content $latestOut -Raw | ConvertFrom-Json

$overallShowRate = [double](Get-JsonValue -Object $today -Path "totals_from_rows.weighted_show_rate")
$overallMatchRate = [double](Get-JsonValue -Object $today -Path "totals_from_rows.weighted_match_rate")
$overallRevenue = [double](Get-JsonValue -Object $today -Path "totals_from_rows.estimated_earnings")
$overallRequests = [double](Get-JsonValue -Object $today -Path "totals_from_rows.ad_requests")
$overallTarget = $OverallShowRateBaseline + $OverallTargetDelta

$latestShowRate = [double](Get-JsonValue -Object $latest -Path "totals_from_latest_rows.weighted_show_rate")
$latestMatchRate = [double](Get-JsonValue -Object $latest -Path "totals_from_latest_rows.weighted_match_rate")
$latestRevenue = [double](Get-JsonValue -Object $latest -Path "totals_from_latest_rows.estimated_earnings")
$latestRequests = [double](Get-JsonValue -Object $latest -Path "totals_from_latest_rows.ad_requests")
$latestTarget = $LatestShowRateBaseline + $LatestTargetDelta

Write-Section "Overall Today"
Write-Host ("Revenue: TRY {0:N2}" -f $overallRevenue)
Write-Host ("Requests: {0:N0}" -f $overallRequests)
Write-Host ("Weighted match rate: {0}" -f (Format-PercentFromFraction $overallMatchRate))
Write-Host ("Weighted show rate:  {0}" -f (Format-PercentFromFraction $overallShowRate))
Write-Host ("Target show rate:    {0}" -f (Format-Percent $overallTarget))
if (($overallShowRate * 100) -ge $overallTarget) {
    Write-Host "Status: target met" -ForegroundColor Green
} else {
    Write-Host ("Status: target not met (gap {0:N2} pts)" -f ($overallTarget - ($overallShowRate * 100))) -ForegroundColor Yellow
}

Write-Section "Latest Versions Today"
Write-Host ("Revenue: TRY {0:N2}" -f $latestRevenue)
Write-Host ("Requests: {0:N0}" -f $latestRequests)
Write-Host ("Weighted match rate: {0}" -f (Format-PercentFromFraction $latestMatchRate))
Write-Host ("Weighted show rate:  {0}" -f (Format-PercentFromFraction $latestShowRate))
Write-Host ("Target show rate:    {0}" -f (Format-Percent $latestTarget))
if (($latestShowRate * 100) -ge $latestTarget) {
    Write-Host "Status: target met" -ForegroundColor Green
} else {
    Write-Host ("Status: target not met (gap {0:N2} pts)" -f ($latestTarget - ($latestShowRate * 100))) -ForegroundColor Yellow
}

Write-Section "Low Performing Apps"
$latestLow = @($latest.low_performing_apps)
if ($latestLow.Count -eq 0) {
    Write-Host "No latest-version low-performing apps detected." -ForegroundColor Green
} else {
    $latestLow | Select-Object -First 10 | ForEach-Object {
        Write-Host ("- {0} | req={1} | match={2} | show={3} | revenue=TRY {4:N2}" -f `
            $_.app, $_.ad_requests, (Format-Percent ([double]$_.match_rate * 100)), (Format-Percent ([double]$_.show_rate * 100)), [double]$_.estimated_earnings)
    }
}

Write-Section "Artifacts"
Write-Host $todayOut
Write-Host $latestOut
