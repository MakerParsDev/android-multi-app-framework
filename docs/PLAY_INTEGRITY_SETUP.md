# Firebase App Check (Play Integrity)

## What We Added

- Firebase App Check initialization:
  - Debug builds: `DebugAppCheckProviderFactory`
  - Release builds: `PlayIntegrityAppCheckProviderFactory`

## Firebase App Check Notes

App Check is installed in `app/src/main/java/com/parsfilo/contentapp/App.kt` via
`FirebaseAppCheckInstaller.install()`.

To actually enforce protection, enable App Check in Firebase Console for the relevant products
(Firestore, Storage, etc.) and switch enforcement on when ready.

## Play App Signing Fingerprints via API (Reality Check)

- Android Publisher API does **not** expose **SHA-1** via a public endpoint.
  For SHA-1, use Play Console -> App Integrity -> App signing.
