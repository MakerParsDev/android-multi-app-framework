# Ads Compliance and Revenue Report (AdMob/GMA/UMP) — February 2026

## Summary
This patch upgrades the app's AdMob/GMA/UMP integration to reduce policy risk and prevent avoidable revenue loss while keeping the existing multi-module architecture intact.

Primary outcomes:
- UMP consent flow now supports age-gate-aware parameters and debug geography controls.
- UMP consent flow now supports cross-app consent sync (`setConsentSyncId`) using Android App Set ID (when available).
- Privacy options are always accessible in Settings.
- ILRD (`onPaidEvent`) logging is centralized and expanded across banner/interstitial/app-open/rewarded/rewarded-interstitial/native.
- Interstitial/App Open cooldown timestamps are recorded on impression instead of before `show()`.
- Load spam is reduced with no-fill/transient-error backoff.
- Banner sizing moved to anchored adaptive (better visibility/fill than the previous fixed inline 50dp approach).
- Placement-based ad unit ID resolution is supported with safe format fallback.
- Native ad pool now has smaller size + TTL pruning.

## Official References Used
- AdMob Android privacy / UMP: https://developers.google.com/admob/android/privacy
- Consent groups / cross-app sync: https://developers.google.com/admob/privacy/consent-groups/sync-consent-across-apps
- GDPR: https://developers.google.com/admob/android/privacy/gdpr
- US states privacy: https://developers.google.com/admob/android/privacy/us-states
- Privacy options entry point: https://developers.google.com/admob/android/privacy/options
- Ad Inspector: https://developers.google.com/admob/android/ad-inspector
- ILRD / `onPaidEvent`: https://developers.google.com/admob/android/impression-level-ad-revenue
- Google Mobile Ads examples (API Demo / Ad Inspector / ILRD snippets): https://github.com/googleads/googleads-mobile-android-examples

## Code Changes (What / Why)

### 1) UMP + Privacy + Age Gate
- `feature/ads/.../AdManager.kt`
  - Added age-gate-aware `ConsentRequestParameters`
  - Added `privacyOptionsRequired` runtime state
  - Added `showPrivacyOptions`, `showConsentFormIfRequired`, `resetConsent`, `openAdInspector`, debug geography controls
  - Applies `RequestConfiguration` based on age-gate state
  - Applies `setConsentSyncId(...)` for AdMob consent group sync using App Set ID (graceful fallback if unavailable)
- `core/datastore/.../PreferencesDataSource.kt`
  - Added stored age-gate fields (`adsAgeGateStatus`, `adsAgeGatePromptCompleted`)
- `feature/settings/.../SettingsViewModel.kt`
  - Persists age-gate choice
  - Exposes `AdManager` debug helpers
- `feature/settings/.../SettingsScreen.kt`
  - Privacy options row always visible
  - Added age-gate selection UI
  - Added debug-only Ads tools (Ad Inspector / consent reset / debug geography)

### 2) ILRD Centralization
- `feature/ads/.../AdRevenueLogger.kt`
  - Central event payload builder and Firebase Analytics logging
- ILRD integrated in:
  - `InterstitialAdManager.kt`
  - `AppOpenAdManager.kt`
  - `RewardedAdManager.kt`
  - `RewardedInterstitialAdManager.kt`
  - `NativeAdManager.kt`
  - `ui/BannerAd.kt`

### 3) Revenue-loss Bug Fixes and Load Quality
- `InterstitialAdManager.kt`
  - Frequency cap timestamp now records on `onAdImpression`
  - Backoff added for repeated load failures / no-fill
- `AppOpenAdManager.kt`
  - Cooldown timestamp now records on `onAdImpression`
  - Backoff added
- `NativeAdManager.kt`
  - Pool reduced and TTL pruning added
  - Backoff added
- `ui/BannerAd.kt`
  - Anchored adaptive banner size
  - Better recomposition lifecycle handling

## Firebase Consent Mode Mapping
- UMP consent outcome is synchronized to Firebase Analytics Consent API (`setConsent`).
- `analytics_storage` and `ad_storage` are updated together from the UMP `canRequestAds()` result.
- Analytics collection starts disabled on cold start and is enabled at runtime only after consent is granted.
- Privacy Options / consent refresh updates Firebase consent + analytics collection state without app restart.
- Crashlytics collection is not disabled by this mapping and continues independently.

### 4) Placement-based Ad Unit Resolution
- `feature/ads/.../AdPlacement.kt`, `AdFormat.kt`
- `app/.../AppAdUnitIds.kt`
  - `resolvePlacement(...)` with fallback to format defaults
- `app/.../AdOrchestrator.kt`
  - Placement-aware preload/show paths (core placements)
- `app/.../AppNavigation.kt`
  - Core route placements wired (home/qibla/zikir/content interstitial paths)

### 5) Manifest
- `app/src/main/AndroidManifest.xml`
  - Added `com.google.android.gms.permission.AD_ID`

## Manual Checklist (AdMob Console)

### Privacy & Messaging (UMP)
- Create and publish **GDPR (EEA/UK)** message.
- Create and publish **US states** message.
- Confirm message scope targets correct regions.
- Verify privacy options entry point requirement behavior on test devices.

### Consent Groups (Rıza Grubu)
- Add all target apps to the consent group only after app-side UMP + `setConsentSyncId(...)` rollout is in production.
- For each app row, resolve `İlgilenilmesi gerekiyor` by confirming published UMP message eligibility and deployment.
- Verify AdMob warning about missing user identifiers is cleared after upgraded app versions are live.
- Update privacy policy to list all apps in the consent group and explain consent synchronization behavior.

### Ad Units / Placements
- Create (optional but recommended) placement-specific ad units matching resource naming convention:
  - Banner examples: `ad_unit_banner_home`, `ad_unit_banner_qibla`, `ad_unit_banner_zikir`
  - Interstitial examples: `ad_unit_interstitial_nav_break`, `ad_unit_interstitial_audio_stop`
  - App open example: `ad_unit_open_app_resume`
  - Rewarded interstitial example: `ad_unit_rewarded_interstitial_history_unlock`
- If a placement resource is missing, the app safely falls back to the format default ID.

### app-ads.txt
- Publish `app-ads.txt` on your verified domain.
- Confirm AdMob shows “Authorized” / verified status.

### Ad Network Scope
- This patch is **AdMob-only**.
- No third-party mediation adapter is required for this setup.

## Manual Checklist (Google Play Console)
- Data safety form matches current ad usage (ads + identifiers + consent behavior).
- Privacy policy URL is set and publicly reachable.
- If any app is enrolled in Families, review family-targeted ad policy requirements separately.

## In-App QA Checklist
- Settings screen always shows **Privacy options** row.
- Privacy options form opens and updates ad behavior in the same session.
- Age-gate selection persists and updates ad serving config without restart.
- Debug build shows **Ads Debug (UMP / GMA)** tools.
- `Open Ad Inspector` works on a debug device.
- `Reset Consent` + `Force EEA/US States` + `Show Consent Form` are usable.

## QA Scenarios
1. **EEA Debug Geography**
   - Force EEA
   - Reset consent
   - Show consent form
   - Deny consent
   - Verify ads are not requested (`canRequestAds=false` path)
   - Verify Firebase DebugView stops receiving new analytics events after deny
2. **US States Debug Geography**
   - Force US states
   - Reset consent
   - Show consent/privacy flow
3. **Age Gate**
   - Choose `16 yaş altıyım` and verify protected ad config is applied
   - Switch back to `16 yaş ve üzeriyim`
4. **Interstitial/AppOpen timestamp bug**
   - Trigger ad show failure (test with invalid unit/network off)
   - Verify cooldown is not incorrectly locked
5. **No-fill / backoff**
   - Observe logs for throttled reload after no-fill (`code=3`)
6. **Banner sizing**
   - Verify anchored adaptive banner displays with appropriate height on multiple screen widths
7. **ILRD**
   - Confirm `ad_paid_event` Firebase analytics events appear (production/staging traffic may be required)
8. **Firebase Consent Mapping**
   - Grant consent and verify Firebase DebugView receives events again without restarting the app

## Monitoring / Operational Notes
- ILRD logs are centralized and PII-free.
- Route values should be low-cardinality route templates (avoid dynamic IDs in analytics).
- Backoff logs help distinguish no-fill from integration errors.
- Consent/Privacy UI changes require AdMob Console messages to be published; code alone is not sufficient.

## Analytics Event Dictionary (Revenue + Consent Funnel)

Core consent events:
- `consent_flow_started`
- `consent_granted`
- `consent_denied`
- `consent_not_required`
- `consent_error`
- `consent_refreshed`
- `privacy_options_opened`
- `age_gate_completed`

Core ad funnel events:
- `ad_request_sent`
- `ad_loaded`
- `ad_failed_to_load`
- `ad_suppressed`
- `ad_impression`
- `ad_failed_to_show`
- `ad_served`
- `ad_click`
- `ad_paid_event`
- `ad_after_engagement`

Rewarded quality events:
- `rewarded_watch_complete`
- `rewarded_watch_skipped`

Key params used for segmentation:
- `ad_format`, `placement`, `route`
- `suppress_reason`, `fill_latency_ms`, `backoff_attempt`, `adapter_name`
- `session_content_type`, `session_duration_s`, `verse_count_before_ad`, `session_audio_played`
- `consent_status`, `consent_trigger`, `ump_form_shown`, `age_gate_result`

## Force Update (Remote Config)
- Force update gate uses Firebase Remote Config with in-app fail-safe defaults (app remains usable if fetch fails).
- Parameters:
  - `min_supported_version_code`
  - `latest_version_code`
  - `update_mode` (`none|soft|hard`)
  - `update_title_tr`, `update_message_tr`, `update_button_tr`, `later_button_tr`
  - `update_title_en`, `update_message_en`, `update_button_en`, `later_button_en`
- Behavior:
  - `currentVersionCode < min_supported_version_code` => hard block (mandatory update)
  - `currentVersionCode < latest_version_code` => soft prompt (dismissible for current session)
  - `min_supported_version_code` rule always has highest priority
- Shared Firebase project strategy:
  - Keys remain global
  - App-specific values are assigned using a Remote Config condition (`app_is_zikirmatik`) matched by Firebase App ID
- Emergency rollout:
  - Soft prompt: increase `latest_version_code`
  - Hard block: increase `min_supported_version_code`
  - Optional `update_mode=hard|soft` can force behavior, but min-supported rule still wins
- Debug validation:
  - Settings > Update Debug > fetch RC now
  - Verify summary values (`current/min/latest/mode/policy`)
  - Simulate soft/hard to validate UI
  - Test update button opens Play Store
- Operational note:
  - Remote Config template JSON is not committed to the repo; use Firebase CLI with a temporary file and `--project` flag.

## Risks / Rollout Notes
- Placement resources missing in a flavor will fall back to format defaults (safe behavior).
- Age-gate wording and legal interpretation may require product/legal review (technical implementation is provided).
- Native click/impression attribution is best-effort; ILRD is the primary revenue source-of-truth for monetization optimization.
