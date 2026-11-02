# Controlled Android Play production rollout

This runbook complements RELEASE_HEALTH_RUNBOOK.md and the repository's
runtime-observability policy. A successful internal-track test, CI run or
single-device FCM test is **not** production approval.

## Mandatory gates

1. Confirm the exact flavor/package, Play-signed versionCode, source SHA,
   internal acceptance and protected main CI and CodeQL.
2. Evaluate version-specific Play Vitals crash and ANR, Crashlytics clusters,
   billing, AdMob technical failures (exclude normal SDK no-fill code 3),
   UMP, push and Remote Config signals with true denominators.
   Missing or delayed data = INCOMPLETE, never zero. Record health decisions
   and explicit accepted risks in the release issue.
3. Release a **single canonical flavor** and **no listing changes**.
4. The release.yml workflow permits an explicitly selected **5%** canary
   or owner-approved **100%** release for one canonical flavor at a time,
   with the manual protected production GitHub environment.
   Missing or invalid rollout percentage fails closed. Internal/build jobs
   must set the rollout percent to 0.
5. The legacy release.yml workflow **builds a new artifact and auto-bumps
   its versionCode**. It is **not** promotion of an already accepted internal
   APK. To ship precisely the same internal-track artifact, use a separately
   reviewed Play Console **Promote release** operation at **5% staged
   rollout**, or a separately verified promotion-only workflow. Verify the
   versionCode and Play-signed artifact identity. Never use a new build while
   claiming to promote an already tested version.

## Stage, pause and recover

- Play Console > Production: verify the requested release is either
  **In progress** at 5% or **Completed** at the explicitly authorized 100%.
  Record the release versionCode, fraction/status and reviewer.
- Monitor release-specific Vitals, Crashlytics, consent, ads, push, billing
  and Remote Config signals. If a safety threshold is crossed, **Halt
  rollout** in Play Console. Halting prevents *further* exposure but does
  not downgrade devices already updated.
- Review fresh evidence before resuming/increasing staged percentages.
  **100% completion** requires existing owner authorization and a deliberate
  full-release selection. Never infer it from CI success alone.
- Confirm Play Console's remote release status and fraction after each
  change. The GitHub workflow success alone is insufficient.

Historical Amenerrasulu v57 acceptance: source PR #363, internal release
#38949238981 and device acceptance #339. The owner subsequently approved
direct 100% production v1.0.60, independently confirmed as COMPLETED on
2026-11-02; see issue #365. Post-release v60 health remains unmeasured.


## Standing owner authorization: full release for any app flavor

The owner authorized production publishing for all 17 apps in this framework
on 2026-11-02, without requesting repeated confirmation for each release.
The 100% option is valid for exactly one canonical flavor per run; it does not
publish all apps automatically, alter the default 0, or relax protected-main
CI/CodeQL, signing, duplicate-release checks, protected production environment,
Play credentials, or live release verification.

A 100% release uses Play ReleaseStatus.COMPLETED without userFraction=1.0,
which is invalid. A 5% release uses IN_PROGRESS at 0.05. Publish one app
using explicit Release build/quality, do_publish=true, do_internal_test=false,
update_play_listing=false and production_rollout_percent=100. No code or
listing changes are implied for other apps.

Full-release authorization is not proof of candidate health: monitor actual
Play Vitals, UMP, AdMob and runtime observations as they become available.
