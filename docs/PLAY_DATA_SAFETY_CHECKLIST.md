# Play Data Safety Checklist

## COMPLIANCE-CRITICAL Data Flows Present in Code
- Advertising SDK:
  - Google Mobile Ads SDK
  - User Messaging Platform
- Analytics:
  - Firebase Analytics custom ad and consent events
- Identifiers:
  - App Set ID usage for consent sync
- Diagnostics:
  - ad load / impression / suppression logging
- Location:
  - `namazvakitleri` uses last-known device location only after explicit permission and user action
  - coordinates are rounded to three decimal places before reverse geocoding (approximately 110 m precision)
  - rounded coordinates are sent to `nominatim.openstreetmap.org` (OpenStreetMap Foundation) to resolve country/city/district
  - no coordinate, request URL, or upstream response body is written to application or Crashlytics logs
  - cache is process-local only: 24-hour fresh TTL, up to seven-day outage fallback, cleared when the app process ends

## Required Review Before Release
- Confirm Data Safety answers still match:
  - advertising or marketing
  - analytics
  - app info and performance
  - device or other identifiers
- Confirm privacy messaging in the app matches actual runtime behavior:
  - consent required geographies
  - privacy options entry point
  - premium / ad-free behavior
  - precise/approximate location declaration for the prayer-location feature
  - OpenStreetMap/Nominatim third-party processing and in-app attribution
  - location retention statement matches the process-local cache policy

## OPS-CRITICAL Notes
- If a new ad SDK or privacy SDK is added, re-run the Data Safety review.
- If child-directed or TFUA behavior changes, review store disclosures again.
- If server-side rewarded verification is introduced later, update disclosures for the new backend data flow.

## Prayer Location Release Evidence
- Confirm the app shows `© OpenStreetMap contributors` beside automatic location matching.
- Confirm a fresh resolve never exceeds one Nominatim request per second.
- Confirm repeated/concurrent rounded-location requests are served from cache or coalesced.
- Confirm 429/5xx/offline responses use controlled retry and stale-cache fallback without logging coordinates.
- Re-review this section if the endpoint, coordinate precision, TTL, or provider changes.
