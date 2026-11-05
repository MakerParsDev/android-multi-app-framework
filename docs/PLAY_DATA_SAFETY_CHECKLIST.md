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

## Account deletion and Data safety verification (#878)

This is a **manual, owner-reviewed** process using the existing Google Play,
Firebase and public Parsfilo site. It does **not** authorize an agent to access
real user records, send email, delete an account, or claim that deletion is automatic.
Use Google Play's [account-deletion requirements](https://support.google.com/googleplay/android-developer/answer/13327111)
and [Data safety instructions](https://support.google.com/googleplay/android-developer/answer/10787469)
when reviewing the actual Console declaration.

### Source-backed data map

| Source | Data potentially tied to a user | Deletion/retention check |
| --- | --- | --- |
| `core/auth/AuthManager.kt` | Firebase Authentication principal created by Google credential sign-in; UID, email and provider profile fields must be checked in the actual Auth record | Verify ownership before acting; delete Auth principal **last** |
| `firestore.rules` `user_messages` | `userId == uid`, messages accessible to their owner | Enumerate the actual UID's messages before removing its Auth access; delete account-linked content unless retention is independently required |
| `functions/src/verifyPurchase.ts` `purchase_verifications` | Server-only UID, optional email, product/package, purchase status, hashed token and update time | Review every matched record for valid financial, fraud, dispute or legal retention; minimize/remove other personal data, preserve genuine purchase evidence only when required |
| `functions/src/registerDevice.ts` `devices` | Installation-ID-associated push token/device diagnostics; Auth UID association **not proven** | Do not bulk-delete or claim all records are UID-linked; investigate only demonstrably related installations |
| Ads/UMP, Firebase Analytics/Crashlytics and other SDKs | App usage, ad/consent events, identifiers and diagnostics may be processed by service providers | Audit actual flavor-specific data types, privacy policy and service-provider deletion/retention before Console submission |

### Handling a real request (operator procedure, not automatic)

1. Receive a **user-initiated** request through the existing public
   `https://parsfilo.com/account-deletion/` page and configured support route.
   Do not treat an email address alone as proof of ownership. Verify app(s),
   account UID and ownership through an approved secure check; never request
   passwords, OTPs, purchase tokens or payment-card numbers.
2. Make a minimum-necessary **private** inventory of UID-linked documents,
   actual provider-held information and lawful retention requirements. Never
   put customer identities, emails or tokens into public issues, logs or CI.
   An unverified installation ID is not evidence of account ownership.
3. Independently determine whether a specific purchase record must be retained
   for a real legal, accounting, fraud or dispute purpose. Do not invent a
   universal statutory period, erase required purchase evidence, or assume
   every record qualifies for an exception. Record the **applicable** period
   and category privately for review and explain it in the request response.
4. Following authorized identity and retention review, handle associated
   messages/provider data before deleting the Firebase Authentication user.
   Verify the requested scope and result. Google Play subscriptions need
   separate user-managed cancellation; **do not purchase/cancel** on behalf
   of the user.
5. Record only minimal private closure evidence and respond through the
   authorized channel. An agent must have explicit authorization for an
   outbound email, message or support submission. Never claim deletion on
   the strength of a static web page or an untested script.

### Google Play declaration gate

- **Mucize Dualar source** supports Google Credential Manager/Firebase Auth
  `signInWithCredential`, which can create an app account. The 5 Nov 2026
  saved Console answer **"app does not allow users to create an account"**
  conflicts with that path. OAuth + the existing public deletion URL is a
  candidate **only after** full Data safety reconciliation.
- Review each affected app's real collection and sharing: **personal info**
  (Firebase identity/email when present), location where supported, messages,
  financial/purchase verification where relevant, app activity, diagnostics,
  ads/consent/analytics identifiers and all provider SDK data. Never copy
  one flavor's answers to all 17 apps without verification.
- The optional saved Console answer implying **automatic data-only deletion
  within 90 days** has **no demonstrated corresponding deletion mechanism**.
  It is not evidence of account-deletion timing. Correct it only as part of
  a complete, independently checked declaration.
- Verify the live public page describes **deletion scope and retention
  exceptions**; before publishing any hard deadline, document its actual
  operational/legal basis. The current public page is a manual **request
  channel**, not evidence of fulfilled user requests.
- Preview all Data safety answers and validate the actual signed app's
  in-app path, link, identity verification and operator procedure **before**
  saving/submitting revised Console data. A source-only PR does not update
  Hosting or Play Console. If Hosting is updated, overlay only reviewed
  changed paths on the existing live release; preserve current public app
  listings and all existing file hashes outside the targeted paths.

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
