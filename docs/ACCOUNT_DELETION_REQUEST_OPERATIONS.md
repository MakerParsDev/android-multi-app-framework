# Account and associated-data deletion — operator procedure

**Applies to:** verified requests for Parsfilo Android applications using Google
sign-in, initially Mucize Dualar (`com.parsfilo.mucizedualar`). This is an
**operator-run request procedure**, not an automatically deployed deletion
endpoint. The user-facing request page is
https://parsfilo.com/account-deletion/ and the in-app Settings link opens it.
No real account, purchase, installation or notification record was accessed
or deleted while preparing this procedure.

## Before touching any records

1. Receive an actual account deletion request via the existing public
   request channel. Record the app, time received and a private case ID in
   an access-controlled operator record **outside public GitHub**. Never
   publish a user's name, email, UID, FCM token or purchase information in
   a public issue, PR or CI log. Do not request passwords, one-time codes,
   payment-card data or purchase tokens.
2. **Verify account ownership** independently before accessing or deleting
   account data. An email address typed in a request is not proof of
   identity: establish control of the Firebase-authenticated Google account
   through a permitted support identity check. If ownership cannot be
   verified, STOP; do not act on an email/UID guess or delete anything.
3. Clarify which app(s) the requester uses. Firebase Authentication
   principals may be shared across app flavors using the same Firebase
   project. Deleting a shared principal can revoke sign-in for more than
   one app: identify this impact and confirm the intended account scope.
   If ownership or scope is ambiguous, STOP.
4. Check active Google Play subscriptions and let the requester know that
   deleting the app account **does not cancel a Google Play subscription**.
   Review possible statutory accounting, refund/dispute or fraud-retention
   obligations with the responsible operator. Record the precise legal
   basis, categories and applicable retention period for any exception;
   **no default 90-day deadline has been established**. Without a
   decision, STOP before any irreversible operation.

## Safe, bounded operator inventory (admin-only)

Use an authenticated, authorized Firebase console/Admin SDK session for
the *verified, exact Firebase project*. Use the following selectors, not
collection-wide scans or an email-only bulk-delete. Never run these
operations from an untrusted client or public browser, and do not commit
service-account credentials.

| Data | Verified source and selector | Deletion decision |
| --- | --- | --- |
| Firebase Authentication principal | Admin Auth `getUserByEmail(verifiedEmail)` to resolve and verify `uid`; do **not** delete yet | Delete **last**, after linked data is resolved |
| Firestore `user_messages` | `feature/messages/.../MessageRepository.kt`: query `where("userId", "==", uid)`; records contain `userName`, `userEmail`, subject and message | Delete **only matched records** after identity verification; review all result pages |
| Firestore `purchase_verifications` | `side-projects/firebase/functions/src/verifyPurchase.ts`: query `where("uid", "==", uid)`; review package name, product and status | Delete matched records unless a **documented** legal/fraud/chargeback exception applies; never erase other buyers' records or active entitlement evidence blindly |
| Firestore `devices/{installationId}` | `registerDevice.ts`: installation-ID document with FCM token, app and device data; **no reliable UID link is stored** | Do not join by email/UID or delete arbitrary devices. Only act on a separately verified installation identifier; otherwise record this as an unresolved identity-mapping limitation |
| Google/Firebase/ads provider-held data | Firebase Installations, Messaging, Analytics, Crashlytics, Performance and Google Mobile Ads disclosures | Identify whether the provider can locate data for this user and the relevant provider deletion/retention mechanism. Do not promise per-user erasure without evidence |

## Provider identity and erasure gate (5 November 2026)

**Do not equate Firebase Auth UID, GA4 app instance ID, Firebase installation
ID (FID), or the app-owned `devices/{installationId}` key.** Verified Auth
ownership establishes only the UID. The current Android analytics gateway
(`core/firebase/.../AppAnalytics.kt`, `AnalyticsPayloadPolicy.kt`) accepts
only `anon_*` analytics user IDs, not Firebase UID or email. No supported
UID-to-GA4-app-instance/FID mapping was established in this source audit.

| Provider data | Supported route / evidence needed | Acceptance decision |
| --- | --- | --- |
| Google Analytics for Firebase / GA4 | Admin API `properties.submitUserDeletion` accepts **one verified** `userId`, `appInstanceId`, `clientId` or `userProvidedData` for the **correct property**; its response records request submission, not completed erasure. `resetAnalyticsData()` resets local on-device analytics state and app instance ID, **not** prior server data by itself. | Never submit another person's or guessed identifier. If no defensible match exists, record the provider limitation; do not certify full erasure. |
| Firebase Installations / FCM / Remote Config / Performance | FID-specific deletion is documented by Firebase. App-owned `devices/{installationId}` and FID are **not interchangeable**. Deleting Firestore device records alone is not proof the Firebase installation was deleted, and deleting an FID can affect other Firebase features. | Require separately verified installation/FID ownership and relevant provider deletion acceptance; otherwise keep unresolved. |
| Crashlytics | Firebase documents a service-specific crash-report retention period (90 days **before removal begins**), not a blanket account-data SLA. Clearing a Crashlytics user identifier, if one was set, does not erase earlier reports. | Review whether a user-specific request is technically possible for the relevant SDK/version; escalate provider support **only with the owner's explicit approval** if necessary. |
| Google Mobile Ads / UMP | The Android Google Mobile Ads SDK declares automatic collection/sharing of IP address, interactions, diagnostics and identifiers. Collection/sharing and provider-side retention are separate from Firebase Auth deletion. | Verify the signed Mucize build and publisher configuration; document available provider erasure path or limitation before Play submission. Do not invent a per-user API. |

For an individual request, record a private, **per-provider** status:
`not_collected`, `verified_deleted`, `verified_request_submitted`,
`documented_retention`, or `unresolved`. Preserve a provider confirmation or
request timestamp when available; **submission is not verified deletion**.
Do not publish account IDs or raw provider identifiers in GitHub or CI.
Any missing identity linkage or legal retention decision is a **STOP gate**
for an unqualified claim that all associated data was erased. This is a
review procedure, not a new tracking database, Firebase service or deletion API.

Official references: [GA4 user deletion API](https://developers.google.com/analytics/devguides/config/admin/v1/rest/v1alpha/properties/submitUserDeletion),
[Firebase installations](https://firebase.google.com/docs/projects/manage-installations),
[Firebase product retention](https://firebase.google.com/support/privacy/),
[Crashlytics user IDs](https://firebase.google.com/docs/crashlytics/android/customize-crash-reports),
[Google Mobile Ads disclosure](https://developers.google.com/admob/android/privacy/play-data-disclosure).

**Important:** Deleting a Firebase Authentication account alone does not
delete Firestore documents. Do not delete the authentication principal
first, because it can destroy the reliable UID association required for
finding dependent data. Deleting a Firestore parent document does not
automatically remove subcollections.

## Execute only for a verified, authorized individual request

1. Record a **dry-run inventory** in the private case (UID match verified,
   per-collection *counts*, entitlement/retention classification, linked
   installation evidence, provider status). No raw personal data in logs.
2. Verify the retention decision and that no other user/app is affected.
   Delete eligible **individual** `user_messages` and
   `purchase_verifications` documents via the trusted Admin SDK or
   Firebase console. Paginate if needed, keep counts bounded, and review
   subcollections separately. No broad query or bulk collection wipe.
3. If a particular `devices/{installationId}` is **independently linked**
   to this verified request, delete that record and handle FCM token
   invalidation using the authorized provider mechanism. Otherwise do not
   guess at the linkage; document the limitation and notify the requester
   as appropriate through the approved support process.
4. Verify the UID-scoped Firestore queries now return no eligible records,
   with every retained record and its basis/period documented. Only then
   remove the Firebase Authentication principal with Admin Auth
   `deleteUser(uid)`, after evaluating shared-app impact.
5. Verify Auth reports the UID as not found and repeat the scoped queries.
   Record completion date, anonymized counts, outstanding provider requests,
   retention exceptions and the outcome in the **private** case. Communicate
   the outcome through the authorized request channel; never claim data
   was fully erased while linkage/provider questions remain unresolved.

**Do not trigger any of these deletes for a demonstration, automated test
against production, casual chat instruction, or unverified request.**
Validate implementation with emulator/fake records first if automation is
introduced later. This document neither executes nor grants permission to
process real user data or to send external support messages.

## Acceptance for #878 and Google Play Data Safety

- In-app and public web request paths verified (TR/EN/DE); current Hosting
  deployment is separate from this operator document.
- Every collected category has justified purposes, collection/sharing,
  optionality, and an accurate user-visible deletion/retention statement.
- An operator can verify a real request and account ownership, locate
  per-UID messages and purchases, decide legitimate retention, avoid
  accidental cross-user/device deletion, process provider-linked data, and
  document completion **without assuming that Auth deletion cascades**.
- Only claim working, timely account-data deletion after the operator path
  has been accepted on **test identities** and any outstanding provider
  limitations are explicit; never invent an automatic deletion SLA.

References: [Google Play account deletion](https://support.google.com/googleplay/android-developer/answer/13327111),
[Firebase Admin Auth user management](https://firebase.google.com/docs/auth/admin/manage-users),
[Cloud Firestore deletion semantics](https://firebase.google.com/docs/firestore/manage-data/delete-data).
