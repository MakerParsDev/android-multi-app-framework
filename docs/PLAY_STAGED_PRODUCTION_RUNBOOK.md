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
4. The legacy release.yml workflow only permits a deliberate **5%** canary
   on production, with the manual protected production GitHub environment.
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

- Play Console > Production: verify the new release is **In progress** at
  **5%**, not Completed/100%. Record release ID, versionCode, fraction,
  timestamp and approving reviewer.
- Monitor release-specific Vitals, Crashlytics, consent, ads, push, billing
  and Remote Config signals. If a safety threshold is crossed, **Halt
  rollout** in Play Console. Halting prevents *further* exposure but does
  not downgrade devices already updated.
- Review fresh evidence before resuming/increasing the percentage.
  **100% completion** is a separate, explicit approval. Do not infer it
  from a successful GitHub Action or canary.
- Confirm Play Console's remote release status and fraction after each
  change. The GitHub workflow success alone is insufficient.

Amenerrasulu v57: source PR #363, internal release #38949238981, actual
device acceptance #339, release-health tracking #365. Production remains
on v56 until sufficient v57 telemetry is available and issue #337's
AdMob/UMP observability blocker has a documented disposition.


## Explicit owner-approved full release (Amenerrasulü only)

The release workflow also supports \`production_rollout_percent=100\` for exactly
\`target_flavors=amenerrasulu\` when the owner explicitly selects immediate full
production. This does **not** change the default \`0\`, nor does it bypass
protected main, exact-head CI/CodeQL, signing, production environment review,
single-flavor enforcement, duplicate-publish protection or Play account checks.
Other flavors remain staged at 5% until separately authorized.

For a full release, Gradle Play Publisher uses \`ReleaseStatus.COMPLETED\`,
**not** \`IN_PROGRESS\` with \`userFraction=1.0\` (invalid in Google Play).
This is a deliberate release decision and cannot be used as a substitute for
an evidence-backed statement that all health metrics are green. Keep the
known health-data gaps recorded, monitor the published version after release,
and preserve existing rollback/stop controls.
