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

## Mucize Dualar Data Safety reconciliation — 5 November 2026

**Scope:** `com.parsfilo.mucizedualar` only, source at
`main=2ce2e000b13ab38301e1ea82b13b683aebf9f86a`.
This is a disclosure *inventory*, not a completed Google Play form or a
deletion-service implementation. Separate each row's **collection, sharing,
required vs optional, purpose, and deletion/retention** before publication.
Do not copy this matrix to other app flavors.

### Live status and Console draft

- **Hosting published:** the revised TR/EN/DE account-deletion request page
  `https://parsfilo.com/account-deletion/` is live as Firebase Hosting
  version `18fe7a1fd741052f`. Both production domains passed 36/36
  static-file SHA-256 acceptance checks. Publishing the page did **not**
  delete any user account, execute an automatic data-removal job, or submit
  a Google Play Data Safety declaration.
- **Saved Play Console draft, independently reloaded and verified:**
  account creation method `OAuth` (Google Credential Manager →
  Firebase `signInWithCredential`), account deletion URL set to the
  public page, and **optional data-only deletion = No**. The old
  "all user data automatically deleted within 90 days" answer had no
  source-backed mechanism and was removed from this **draft**.
  **No Play form submission or approved store-label change.**
- **Current Data types screen before the new selections:** Personal
  information **0/9**, Financial information **0/4**, Messages **0/3**,
  App activity **0/5**; Crash logs + Diagnostics **2/3**, Device or other
  identifiers **1/1**. Some additional candidate checkboxes were explored
  in the browser but **not saved** because the subsequent browser read was
  blocked by its security control. Do not describe those selections as
  persistent, validated answers.

### Source-backed category candidates (not yet submitted)

| Google Play type | Evidence in this app | Required review before final answer |
| --- | --- | --- |
| Personal info → **Name**, **Email address**, **User IDs** | `core/auth/.../AuthManager.kt` Google ID token → Firebase Auth; `feature/messages/.../MessageRepository.kt` stores `userName`, `userEmail`, `userId` with messages; purchase verification stores UID/optional email | Optional sign-in, account/profile data purposes, service-provider sharing exception, and lifecycle |
| Messages → **Other in-app messages** | `MessageRepository.kt` stores user-initiated `subject`, `message`, `category` in Firestore `user_messages` | Optional support feature, message retention and operator-verified removal |
| Financial info → **Purchase history** | `side-projects/firebase/functions/src/verifyPurchase.ts` persists `productId`, purchase type/state, expiry and token **hash** in `purchase_verifications` | Only purchase/verification history; **no evidence of collecting payment card numbers**. Confirm lawful exceptions and actual retention periods |
| App activity → **App interactions** | `core/firebase/AppAnalytics.kt` and `Analytics*.kt` log app, content, ad and consent events; Google Mobile Ads SDK additionally collects product interactions | Separate optional consent-gated vs automatic/limited ads events; determine purposes and SDK sharing |
| Location → **Approximate location (potential SDK collection)** | Google Mobile Ads SDK documents collecting IP addresses that may be used to estimate approximate location | Verify exact Mucize production SDK configuration and purpose; **not a Mucize GPS claim** |
| App info and performance → **Crash logs**, **Diagnostics** | Firebase Crashlytics, Firebase Performance, Google Mobile Ads diagnostics; these two types are already selected | Provider defaults, custom crash metadata, retention and optional toggles |
| Device or other IDs → **Device or other identifiers** | Firebase Installations, push registration `registerDevice.ts` (installation ID/FCM token), Google Mobile Ads device identifiers | Existing type selected; verify collection/share/purpose and account-to-installation mapping before deletion |

**Do not select solely from an app's subject matter:** prayer content is
not itself a declaration that the app collected the user's **religious
beliefs**; there is no supported claim here that Mucize transmits precise
GPS coordinates, SMS content, payment-card details, health data, contacts
or user recordings. `MainActivity.kt` limits the device GPS permission
flow to `productDefinition.isPrayerTimesFlavor`; the separate
`namazvakitleri` flavor needs its own precise-location review. This
does **not** override the AdMob SDK's possible IP-derived approximate
location category.

### Remaining mandatory verification

1. In step 3 select every **actually collected/shared** category above
   after the exact signed Mucize SDK/runtime review. Do not assume a
   type is absent simply because the app's code does not directly read it.
2. In step 4 record per type: collected vs shared, user-optional vs
   always-collected, ephemeral vs retained, and justified purposes.
   Transfers to contracted *service providers* may qualify for
   Google Play's sharing exclusion; do not label **all** Google SDK
   transfers as excluded without reviewing the specific AdMob/Analytics
   usage. Google Mobile Ads documentation states that some data is
   collected **and shared** automatically.
3. Check storage/deletion **per provider**: Authentication principal,
   Firestore messages, purchase verification records, Firebase
   installation/push identifiers, Analytics, Crashlytics, Performance
   and advertising data. No TTL or 90-day auto-delete mechanism is
   established for the app-owned Firestore collections.
4. Determine and document the **actual** processing procedure and any
   legitimately required retention category/period before claiming one
   publicly. Firebase's provider-specific post-deletion windows do **not**
   establish a universal app data deletion deadline. The public page's
   manual request route alone is not proof of fulfillment.
5. Preview all five Console steps and send for review **only** after
   the account/deletion answers, classification, SDK purposes, privacy
   policy, real request fulfillment and live web disclosure agree.

**Authoritative references (checked 2026-11-05):**

- Google Play data types, collection/sharing and service-provider exemptions:
  https://support.google.com/googleplay/android-developer/answer/10787469
- Google Play account deletion requirements:
  https://support.google.com/googleplay/android-developer/answer/13327111
- Firebase Android SDK data disclosure:
  https://firebase.google.com/docs/android/play-data-disclosure
- Firebase service privacy and deletion/retention specifics:
  https://firebase.google.com/support/privacy/
- Google Mobile Ads Android SDK disclosure:
  https://developers.google.com/admob/android/privacy/play-data-disclosure

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
