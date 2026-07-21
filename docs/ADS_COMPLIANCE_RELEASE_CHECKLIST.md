# Ads Compliance Release Checklist

Last reviewed: 2026-04-29

## Code Gates

- Run `python scripts/ci/validate_app_ads_txt.py --mode strict`.
- Run `python scripts/ci/validate_admob_inventory.py --mode strict --target-flavors all`.
- Run `python scripts/ci/validate_ci_apps_catalog.py --mode strict --target-flavors all`.
- Run `./gradlew :feature:ads:testDebugUnitTest --continue`.
- Run the selected release build or publish workflow without committing local secrets.
- Confirm debug builds resolve only Google test ad units.
- Confirm release builds have `USE_TEST_ADS=false` and do not resolve Google test ad units.
- Confirm `MobileAds.initialize()` is not on the cold-start UI critical path and can retry after a soft timeout.
- Confirm `OPTIMIZE_INITIALIZATION` and `OPTIMIZE_AD_LOADING` manifest metadata remain enabled.
- Confirm `AdsConsentRuntimeState.canRequestAds` is the process-level ad request gate.
- Confirm `requestConsentInfoUpdate()` is called on cold start before fresh ad serving decisions.
- Confirm privacy options remain reachable from Settings when UMP reports they are required.
- Confirm debug geography/test-device behavior is debug-only and not active for release builds.

## Ad Format Checks

- Banner: request, load, fail, impression, click, paid-event, and suppression logging are wired.
- Native: request, load, fail, paid-event, served/not-loaded/suppression logging are wired; native assets must remain registered through the Google native view API.
- App open: request, load, fail, show attempt, not-loaded, started, impression, click, dismiss, failed-show, paid-event, cooldown, and session cap logging are wired.
- Interstitial: request, load, fail, show attempt, not-loaded, started, impression, click, dismiss, failed-show, paid-event, cooldown, and session cap logging are wired.
- Rewarded: request, load, fail, show attempt, not-loaded, served, started, impression, click, dismiss, failed-show, reward callback, and paid-event logging are wired.
- Rewarded interstitial: explicit intro/confirmation remains required; show attempt, not-loaded, served, started, impression, click, dismiss, failed-show, reward callback, token validation, and paid-event logging are wired.

## Remote Config Checks

- Keep the canonical interstitial cap key: `ads_interstitial_frequency_cap_ms`.
- Do not add duplicate keys such as `interstitial_min_interval_seconds`.
- Review these policy keys before publishing a template:
  - `ads_interstitial_frequency_cap_ms`
  - `ads_interstitial_relaxed_frequency_cap_ms`
  - `ads_interstitial_max_per_session`
  - `ads_app_open_cooldown_ms`
  - `ads_app_open_resume_gap_ms`
  - `ads_app_open_max_per_session`
  - `ads_rewarded_max_per_session`
  - `ads_rewarded_interstitial_min_interval_ms`
  - `ads_rewarded_interstitial_max_per_session`
  - `ads_native_pool_max`
  - `ads_native_ttl_ms`
  - placement disabled CSV keys
  - route blocklist CSV keys

## Manual Console Checks

These must be checked in AdMob or Play Console. Do not claim them as code-verified.

1. AdMob Privacy and Messaging GDPR message is active.
2. US states / regulated region message status is correct where applicable.
3. TCF/GDPR settings are correct for the apps.
4. Consent syncing is enabled for eligible apps.
5. app-ads.txt status is `found` / `verified`.
6. Ad Inspector passes on at least one debug device.
7. EEA debug geography shows the expected UMP form in debug builds.
8. Release candidate does not use test ads.
9. Play Console Data Safety covers ads, analytics, consent, app set ID use, and data sharing.
10. The developer website for all 17 Play listings points at the app-ads.txt root domain.
11. Store listing metadata and screenshots remain accurate if Gradle Play Publisher listing upload is enabled.

## Source References

- Google Mobile Ads Android optimize initialization/loading: https://developers.google.com/ad-manager/mobile-ads-sdk/android/optimize-initialization
- Google AdMob Android privacy/UMP: https://developers.google.com/admob/android/privacy
- Google Mobile Ads consent syncing: https://developers.google.com/ad-manager/mobile-ads-sdk/android/privacy/sync-consent
- Google Mobile Ads paid events: https://developers.google.com/admob/android/impression-level-ad-revenue
