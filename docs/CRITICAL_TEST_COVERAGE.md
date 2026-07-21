# Critical Runtime Test and Coverage Matrix

The repository uses two complementary coverage gates:

1. Kover's repository-wide line threshold catches broad regressions.
2. `validateCriticalCoverage` enforces explicit line/branch thresholds for named runtime decisions in `config/critical-coverage.json`.

A green global percentage is not sufficient by itself. A critical target that disappears from the XML report, has no measurable lines, or drops below its configured threshold fails `qualityCheck`.

## Current critical matrix

| Runtime area | Module | Unit-test scope | Coverage target | Boundary kept outside JVM unit tests |
| --- | --- | --- | --- | --- |
| Remote Config update policy | `app` | Hard/soft/none priority, mode normalization, version coercion, locale fallback, key/default registry | `UpdatePolicyKt` plus the `update` package | Firebase Remote Config fetch/activate and Play update UI |
| Push registration retry | `core:firebase` | 4xx/5xx decisions, retry limit, IO/cancellation/non-IO failures, jitter bounds | `PushRegistrationRetryPolicyKt` plus the push package | Firebase token `Task`, real HTTP transport, backend authentication |
| Billing verification response | `feature:billing` | Real `JSONObject` decoding under Robolectric, response mapping, safe defaults, expiry handling, decoder failure, auth/misconfiguration guards | `BillingVerificationResponseParserKt` plus the billing package | Firebase identity/App Check and the live verification endpoint |
| Zikir reminder scheduling | `feature:counter` | Exact/inexact mode, API boundary, same-day/next-day trigger, time clamping, streak-check delay | `ZikirReminderSchedulePolicyKt` plus the alarm package | `AlarmManager`, `PendingIntent`, exact-alarm permission UI, WorkManager execution |

The production boundary classes remain thin. Android/Firebase/network calls delegate to deterministic policy or mapping functions that can be tested without an emulator, wall clock, network, or service account.

## Gate commands

Run the complete blocking gate:

```bash
./gradlew qualityCheck --continue --stacktrace --no-daemon --max-workers=1
```

Run only the aggregate report and critical thresholds:

```bash
./gradlew :validateCriticalCoverage --stacktrace --no-daemon --max-workers=1
```

Generated reports:

- XML: `build/reports/kover/xml/coverage.xml`
- HTML: `build/reports/kover/html/index.html`
- Threshold configuration: `config/critical-coverage.json`

## Threshold policy

- Class thresholds protect the decision code introduced for a critical runtime path.
- Package thresholds are deliberately lower and act as a non-zero floor while broader integration tests are added.
- Thresholds may be raised when tests improve.
- A threshold reduction requires an explicit rationale in the pull request and must not be used to hide a regression.
- Generated code, DI output, Compose-generated classes, Room implementations, and `BuildConfig` are excluded from the aggregate report.

## Test boundary and exceptions

JVM tests are the default for deterministic business decisions. Instrumented/device tests are required when the behavior depends on Android framework state that cannot be represented faithfully by a pure input/output boundary. Current device-level follow-up areas include:

- exact-alarm permission revocation and `SecurityException` fallback on Android 12+;
- actual `AlarmManager` and WorkManager scheduling semantics;
- Firebase token/App Check/Auth task integration;
- backend purchase-verification authentication and response contract;
- Compose/UI interaction and navigation state;
- audio service/session lifecycle and process recreation.

These are explicit follow-up areas, not silent exclusions from the coverage gate.

## Planned expansion

Subsequent batches should add named thresholds and tests for:

1. ad load/show policy and consent gating;
2. audio playback/session state transitions;
3. settings, authentication, and notification orchestration;
4. datastore migration and repository cache decisions;
5. emulator smoke tests for framework-dependent scheduler and service paths.
