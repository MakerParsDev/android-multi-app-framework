# System Broadcast Receiver Security

## Scope

This policy covers the alarm-reschedule receivers used by the `zikirmatik` and
`namazvakitleri` flavors. Widget receivers and explicit alarm `PendingIntent`
receivers are outside this policy.

## Security decision

Both reschedule receivers are `android:exported="false"`. Android still permits
messages sent by the system, the same application, or the same UID to reach a
non-exported receiver. No normal-protection-level permission is treated as
sender authentication.

The receiver rejects every action before dependency resolution or `goAsync()`
unless it is one of:

- `BOOT_COMPLETED`
- `TIME_SET`
- `TIMEZONE_CHANGED`
- `MY_PACKAGE_REPLACED`

The first three are documented manifest-receiver exceptions to Android 8+
implicit-broadcast limits. `MY_PACKAGE_REPLACED` is the package-targeted action
sent only for the application that was replaced; the broader
`PACKAGE_REPLACED` action is not registered.

## Runtime policy

- Duplicate broadcasts share one process-wide execution gate.
- Zikir alarm replacement and WorkManager periodic scheduling remain idempotent.
- Prayer alarm scheduling cancels/replaces the prior explicit `PendingIntent`.
- Async receiver work has an eight-second deadline, below the platform's short
  broadcast execution window.
- `PendingResult.finish()` runs from `finally` after success, failure, timeout,
  or cancellation.
- Unsupported/custom actions do not resolve Hilt dependencies and create no
  background work.

## API 24–37 verification matrix

| Range | Relevant platform behavior | Verification |
|---|---|---|
| API 24–25 | Pre Android 8 implicit-broadcast restrictions | Robolectric API 24 negative/positive receiver tests |
| API 26–30 | Manifest implicit-broadcast limits apply; boot/time/timezone remain exempt | Action allowlist tests and exact merged-manifest action set |
| API 31–33 | Explicit `android:exported` is mandatory for filtered components | Both merged manifests require `exported=false` |
| API 34–36 | Current background and broadcast execution limits | Robolectric API 36 tests, eight-second timeout, coalescing |
| API 37 | Android 17 compile/merge compatibility | compileSdk 37 merged-manifest validation and repository build gate |

The repository task `validateSystemReceiverManifests` builds and parses the
merged debug manifests for both flavors. It fails if a receiver is exported,
permission-gated instead of non-exported, duplicated, or contains a missing or
unexpected action.

## Manual device checks

Negative explicit spoof check:

```bash
adb shell am broadcast \
  -a com.attacker.FORCE_RESCHEDULE \
  -n <application-id>/<receiver-class>
```

The receiver must not be externally invokable and no alarm/worker scheduling
telemetry should appear. Positive boot/time/timezone behavior should be checked
on internal builds through the corresponding system event, not by adding a
custom exported test action.
