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
