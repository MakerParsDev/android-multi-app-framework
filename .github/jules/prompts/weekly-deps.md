# Weekly Dependency Maintenance

Scope:

- Inspect `gradle/libs.versions.toml`, Gradle wrapper metadata, build plugins, and dependency constraints.
- Use current official vendor documentation and Maven Central before changing versions.
- Never downgrade a dependency.
- Never use alpha, beta, rc, milestone, dev, snapshot, or unpublished versions.
- Preserve Compose BOM and Firebase BOM ownership. Do not replace BOM-managed modules with pinned leaf versions.
- Treat AdMob, UMP, Billing, Firebase, AGP, Kotlin, KSP, Hilt, Room, and Gradle wrapper changes as high-risk.

KSP rule:

- Do not assume the old `2.x.y-1.0.z` KSP format.
- Verify the latest stable `com.google.devtools.ksp` release and Kotlin compatibility from official KSP release notes and Maven Central metadata.
- If compatibility is unclear, leave the current version and report why.

Validation:

- Run `./gradlew --version`.
- Run `./gradlew validateFlavorVersions`.
- Run the smallest relevant compile/test/lint commands for changed modules.
- If Android SDK, JDK, Play, Firebase, or secrets are missing, classify the blocker as environment-only or code-caused.

PR requirements:

- PR title must start with `chore:`.
- Include changed dependencies, sources checked, commands run, failures, and manual release gates.
- Do not auto-merge.
