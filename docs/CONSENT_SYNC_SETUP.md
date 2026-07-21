# Consent Sync Setup

Last reviewed: 2026-04-29

## Code State

- Consent sync support already exists in `feature/ads`.
- `ConsentSyncIdProvider` fetches the Google App Set ID with `AppSet.getClient(context).appSetIdInfo.await()`.
- `AdManager.buildConsentRequestParameters(...)` passes the value into `ConsentRequestParameters.Builder.setConsentSyncId(...)` only when a valid ID is available.
- If App Set ID fetch fails, returns blank, or does not meet the consent sync ID format rules, the UMP flow continues without a sync ID.
- No raw user ID, installation ID, email, advertising ID, or other personal identifier is used for consent sync.

## ID Rules Enforced Locally

`ConsentSyncIdValidator` enforces:

- minimum length: 22 characters
- maximum length: 150 characters
- allowed characters equivalent to `[0-9a-zA-Z+.=/_-$,{}]`
- surrounding whitespace is trimmed
- embedded whitespace and unsupported punctuation are rejected

Tests:

```bash
./gradlew :feature:ads:testDebugUnitTest --continue
```

Relevant unit coverage:

- `ConsentSyncIdProviderTest`
- `AdsConsentRuntimeStateTest`

## Manual AdMob Console Setup

This cannot be verified from source code:

1. Open AdMob.
2. Go to Privacy and Messaging.
3. Enable consent syncing for the eligible apps in this project.
4. Confirm the apps share the same consent message configuration where required.
5. Run an EEA debug geography test on a debug device.
6. Confirm no release build has test geography or test-device-only settings enabled.

## Source References

- Google Mobile Ads consent syncing: https://developers.google.com/ad-manager/mobile-ads-sdk/android/privacy/sync-consent
- Google Play services App Set ID: https://developers.google.com/android/reference/com/google/android/gms/appset/AppSet
