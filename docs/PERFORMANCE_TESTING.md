# Android Performance Testing

The repository has two separate performance authorities:

1. **Gradle Managed Device profile generation** produces reproducible Baseline
   Profile and Startup Profile source files for every app flavor.
2. **A serial physical Android device** produces startup and frame timing
   evidence. Emulator timings are diagnostic and never release gates.

## Generate a profile locally

Install the pinned SDK plus the API 33 AOSP image:

```bash
bash scripts/ci/setup-android-sdk.sh
bash scripts/ci/setup-performance-device-sdk.sh
```

Generate one profile pair:

```bash
flavor=kuran_kerim
python3 scripts/ci/generate_ci_google_services.py --flavors "$flavor"
task="$(python3 scripts/ci/performance_profile_policy.py task --flavor "$flavor")"
./gradlew ":app:$task" \
  -PciSmoke=true \
  -Pandroid.testInstrumentationRunnerArguments.androidx.benchmark.enabledRules=BaselineProfile \
  -Pandroid.testInstrumentationRunnerArguments.androidx.benchmark.suppressErrors=EMULATOR \
  -Pandroid.testoptions.manageddevices.emulator.gpu=swiftshader_indirect \
  --no-daemon --no-configuration-cache --stacktrace --max-workers=2
python3 scripts/ci/performance_profile_policy.py validate-source --flavor "$flavor"
python3 scripts/ci/generate_ci_google_services.py --clean --flavors "$flavor"
```

Generated files live under:

```text
app/src/<flavor>Release/generated/baselineProfiles/baseline-prof.txt
app/src/<flavor>Release/generated/baselineProfiles/startup-prof.txt
```

## Physical runner contract

The GitHub runner must have both labels:

```text
self-hosted
android-performance
```

It must expose exactly one authorized, non-personal Android device through ADB.
The runner and device are dedicated to benchmark work. Do not sign into personal
accounts, restore personal backups, or store user data on the device.

Keep the measurement environment stable:

- fixed device model, OS build, and security patch;
- fixed screen brightness and animation-scale policy;
- battery above 50%, unplugged or consistently powered according to the runner policy;
- no thermal throttling at run start;
- no background app installs, sync, backup, or system update;
- stable USB connection and one ADB serial only;
- the same locale, font scale, display size, and accessibility configuration;
- at least three complete observation runs before activating a regression threshold.

The harness records model, fingerprint, API level, ABI, battery, thermal state,
storage, animation scales, logcat, benchmark JSON, and Perfetto traces.

## Run connected benchmarks

All flavors, all suites:

```bash
bash scripts/ci/run_physical_performance.sh all all 10 build/physical-performance
```

One flavor and startup only:

```bash
bash scripts/ci/run_physical_performance.sh kuran_kerim startup 10 build/physical-performance
```

One flavor and frame timing only:

```bash
bash scripts/ci/run_physical_performance.sh zikirmatik frames 10 build/physical-performance
```

Iteration input is clamped to `5..30`. Runs are deliberately serial and Gradle
uses one worker to reduce host-side contention.

## Artifact locations

Primary evidence:

```text
build/physical-performance/device-metadata.txt
build/physical-performance/<flavor>/logcat.txt
build/physical-performance/<flavor>/**.json
build/physical-performance/<flavor>/**.perfetto-trace
performance/benchmark/build/outputs/
performance/benchmark/build/reports/
```

GitHub retains physical-run artifacts for 30 days. Managed-device profile jobs
retain profile and diagnostic artifacts for 14 days.

## Regression gates

Do not create a release threshold from a single run. Establish a baseline from
at least three complete runs on the same physical device and OS build. Review
median and tail behavior for cold startup, warm startup, frame duration, and
profile/no-profile comparisons. Threshold activation is a reviewed repository
configuration change, not an ad hoc workflow input.

When a device, OS build, benchmark library, toolchain, or critical user journey
changes, start a new observation window before using the new measurements as a
release gate.
