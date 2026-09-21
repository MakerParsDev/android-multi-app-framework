# Dependency Lifecycle Policy

## Scope

This policy governs Gradle, Android, Kotlin, Compose, Firebase, Google Play, AdMob, Billing, Jetpack, test, and build-plugin dependencies in this repository.

Authoritative files:

- `gradle/libs.versions.toml`: direct library and plugin versions.
- `config/dependency-policy.json`: stable-only policy and temporary prerelease exceptions.
- `config/gradle-warning-policy.json`: owned Gradle, D8, and ASM warning fingerprints with review deadlines.
- `gradle.properties`: Android/JVM toolchain manifest.
- `settings.gradle.kts`: explicitly forced security versions for build-plugin transitive dependencies.

## Stable-only rule

Direct catalog versions must be stable and exact.

The following are blocked by `auditDependencyCatalog`:

- `alpha`, `beta`, `rc`, `preview`, `eap`, `snapshot`, milestone, and similar prerelease versions without an approved exception.
- Dynamic versions such as `1.+` or `latest.release`.
- Version ranges such as `[1.0,2.0)`.
- Inline library or plugin versions outside the version catalog.
- Missing `version.ref` keys.
- Expired or incomplete allowlist entries.

A prerelease exception must include:

- the exact catalog alias or transitive coordinate;
- an accountable module owner;
- a concrete compatibility reason;
- an `expires_on` review deadline.

Broad `*:*` exceptions are forbidden. Expired exceptions fail the quality gate.

## Generated audit reports

`./gradlew auditDependencyCatalog` produces:

- `build/reports/dependencies/catalog-audit.json`
- `build/reports/dependencies/catalog-audit.md`

The report inventories version keys, library aliases, plugin aliases, version sources, unused version keys, catalog prereleases, and transitive prerelease exceptions. The normal quality artifact collector includes these files automatically.

Quality runs also execute Gradle with `--warning-mode all` and preserve the complete output at:

- `build/reports/dependencies/gradle-quality.log`
- `build/reports/dependencies/gradle-warning-audit.json`
- `build/reports/dependencies/gradle-warning-audit.md`

`validate_gradle_warning_report.py` blocks any new Gradle deprecation, D8 warning, or ASM unresolved-class warning that does not match an owned, unexpired policy entry. Android Lint HTML/SARIF reports remain the blocking deprecated/obsolete API control. The warning ratchet supplements lint with Gradle, D8, ASM, and plugin lifecycle diagnostics.

## Dependency update strategy

The repository uses **GitHub Actions** and **Dependabot** for automated dependency tracking across all monorepo ecosystems (Gradle, GitHub Actions, pip, and npm side projects):

- Dependabot runs weekly on Sundays (`schedule: interval: weekly`);
- Patch and minor updates are grouped to reduce noise;
- High-risk dependencies (Gradle, Kotlin, AGP, KSP, Hilt, Room, Firebase plugins, Play Billing) are isolated and never grouped;
- **Mergify** governs all dependency pull request queues. Only Class A low-risk dev patch/minor updates are eligible for auto-merging after passing all required quality and security gates;
- Major updates and sensitive toolchains require explicit human review and approval;
- Continuous monitoring and drift detection are managed by `.github/workflows/maintenance-health.yml`.

## Upgrade pull-request gate

Every CI run executes the required verification gates:

- `validateFlavorVersions`;
- affected application and Android library tasks;
- ktlint and Detekt static analysis;
- aggregate Kover reports and critical coverage verification;
- dependency catalog audit (`auditDependencyCatalog`);
- CodeQL Java and Kotlin analysis;
- SonarCloud quality gate;
- Security gate (Gitleaks, Semgrep SAST/OSS, Dependency Review).

A dependency upgrade must not be merged unless all blocking checks pass. Autonomous merging is restricted strictly to qualified low-risk PRs as defined in `docs/AUTONOMOUS_MAINTENANCE.md`.

## Adding a new dependency

1. Confirm the feature cannot be implemented with the platform or an existing dependency.
2. Review maintenance activity, license, release stability, minimum SDK, transitive graph, and known security advisories.
3. Add the version to `[versions]` and reference it with `version.ref`; do not add an inline version.
4. Prefer a stable release. Add a time-limited policy exception only when a stable top-level SDK requires a prerelease transitively.
5. Add the dependency to the narrowest owning module; avoid adding it to `app` when a feature/core module owns the behavior.
6. Run `./gradlew auditDependencyCatalog staticQualityCheck`.
7. Run the complete local verification and release task-graph dry-run before requesting merge.
8. Document runtime, privacy, Play policy, or binary-size impact when applicable.

## Upgrade procedure

1. Change one lifecycle family per pull request where practical: Android toolchain, Kotlin/KSP, Compose, Firebase, Play/Ads/Billing, or an isolated library.
2. Refresh the dependency audit report and inspect the resolved graph.
3. Run the full quality gate.
4. Review `gradle-quality.log` for new deprecations, D8 warnings, ASM resolution warnings, or removed APIs.
5. Validate representative app startup/runtime behavior when the dependency affects initialization, consent, billing, ads, push, database, audio, or background scheduling.
6. Record migration notes and rollback information in the pull request.

## Play Asset Delivery note

The project uses `com.google.android.play:asset-delivery-ktx:2.3.0` for on-demand audio asset packs. The Android Play Asset Delivery integration guide currently documents `2.3.0`; therefore the dependency remains pinned rather than being changed only to suppress a D8 warning.

The existing D8 stack-map warning must remain visible in `gradle-quality.log`. A future change requires an official compatible release, a verified removal of the asset-pack fallback, or a documented toolchain-level fix.

Official reference:

- https://developer.android.com/guide/playcore/asset-delivery/integrate-java

## Gradle deprecation handling

`--warning-mode all` is required in quality and dependency-audit runs. New warnings are assigned to one of these owners:

- repository build scripts/buildSrc;
- Android Gradle Plugin or Kotlin/KSP plugin;
- quality plugins;
- direct dependency;
- transitive dependency/tooling-only class.

Repository-owned deprecations are fixed in the current maintenance cycle. Third-party warnings are documented with the dependency coordinate, current version, owner, reason, and next review date in `config/gradle-warning-policy.json`. The current Gradle 10 warning is attributed to Detekt 1.23.8; the PAD D8 and Android ASM instrumentation warnings remain separately owned and visible.

Official reference:

- https://docs.gradle.org/current/userguide/command_line_interface.html#sec:command_line_warnings

## Gradle dependency verification decision

Gradle dependency verification was evaluated for checksum/signature metadata across the complete multi-flavor graph. The current decision is **deferred until 1 October 2026**, not rejected. Full verification metadata would introduce a large first-time trust set before Android/plugin artifact churn is measured.

Blocking controls during the deferral are checksum-pinned Gradle distribution and wrapper JAR, stable-only catalog policy, resolved-graph prerelease rejection, Socket reporting, scheduled dependency audits, and checksum-pinned security tooling. `config/supply-chain-policy.json` enforces the review date. If the decision changes to `enabled`, `gradle/verification-metadata.xml` becomes mandatory.
