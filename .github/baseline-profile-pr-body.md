## Generated Baseline Profiles

This pull request refreshes the variant-scoped Baseline Profile and Startup
Profile pair for every cataloged Android flavor.

Validation:

```bash
python3 scripts/ci/performance_profile_policy.py validate-all
python3 scripts/ci/performance_profile_policy_test.py
```

The profile files are generated on a Gradle Managed Device. Emulator timing
values are diagnostic only and are not release gates; authoritative startup and
frame comparisons run on the serial physical-device performance runner.

Expected diff boundary:

```text
app/src/*Release/generated/baselineProfiles/baseline-prof.txt
app/src/*Release/generated/baselineProfiles/startup-prof.txt
```
