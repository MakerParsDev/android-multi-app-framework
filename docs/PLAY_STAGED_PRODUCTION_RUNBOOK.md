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
4. The release.yml workflow permits only an explicitly selected
   owner-approved **100%** release for one canonical flavor at a time,
   with the manual protected production GitHub environment.
   Missing or invalid rollout percentage fails closed. Internal/build jobs
   must set the rollout percent to 0.
5. The legacy release.yml workflow **builds a new artifact and auto-bumps
   its versionCode**. It is **not** promotion of an already accepted internal
   APK. To ship precisely the same internal-track artifact, use a separately
   reviewed Play Console **Promote release** operation to **100%**,
   or a separately verified promotion-only workflow. Verify the
   versionCode and Play-signed artifact identity. Never use a new build while
   claiming to promote an already tested version.

## Stage, pause and recover

- Play Console > Production: verify the requested release is
  **Completed** at the explicitly authorized 100%.
  Record the release versionCode, fraction/status and reviewer.
- Monitor release-specific Vitals, Crashlytics, consent, ads, push, billing
  and Remote Config signals. If a safety threshold is crossed, **Halt
  rollout** in Play Console. Halting prevents *further* exposure but does
  not downgrade devices already updated.
- Review fresh evidence before publishing or resuming any production release.
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
which is invalid. New 5% staged production releases are not permitted.
Publish one app using explicit Release build/quality, do_publish=true, do_internal_test=false,
update_play_listing=false and production_rollout_percent=100. No code or
listing changes are implied for other apps.

## Promote the exact accepted Internal version without rebuilding

When a signed Internal version has passed the real-device and release-health
acceptance gates, use **Actions > Promote accepted Play Internal release**
on protected main. This is distinct from release.yml, which builds and
auto-increments a new AAB and therefore cannot guarantee artifact parity.

1. Record the exact current Internal and Production versionCode values from
   Google Play, the app flavor and an issue containing **completed** acceptance
   evidence. The default preview mode checks these against the live Play
   tracks but does not commit any release change.
2. If the preview succeeds, verify the candidate's signed Play installation,
   relevant CI/CodeQL, and complete eight-signal health evidence; unresolved
   issue #1160 acceptance remains a blocker, not a release approval.
3. Select publish only after acceptance. The existing protected production
   environment gates the job. The script references the same already-uploaded
   Internal versionCode, without building or uploading another AAB; it aborts
   if either track changed or if Production has an in-progress/halted release.
4. Publication remains **100% only**, consistent with owner decision #1060.
   This workflow does not introduce a 5% rollout or silently change that policy.
   After commit it reads the Production track again; an ambiguous result must
   be inspected in Play Console **before retrying**. A successful read-back
   proves the Play API state, not immediate installation or runtime health.
5. Follow the 24/48/72-hour health checkpoints; react to proven regressions
   using the Play Console incident process. No action is automatic.
Full-release authorization is not proof of candidate health: monitor actual
Play Vitals, UMP, AdMob and runtime observations as they become available.
