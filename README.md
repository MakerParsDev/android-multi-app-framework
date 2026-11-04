# Android Multi-App Framework (ContentApp)

[![Main Quality Gate](https://github.com/MakerParsDev/android-multi-app-framework/actions/workflows/ci-main.yml/badge.svg?branch=main)](https://github.com/MakerParsDev/android-multi-app-framework/actions/workflows/ci-main.yml)
[![CodeQL](https://github.com/MakerParsDev/android-multi-app-framework/actions/workflows/codeql.yml/badge.svg?branch=main)](https://github.com/MakerParsDev/android-multi-app-framework/actions/workflows/codeql.yml)
[![GitGem](https://gitgem.org/api/badge/github/MakerParsDev/android-multi-app-framework.svg)](https://gitgem.org/github/MakerParsDev/android-multi-app-framework)

A modular Kotlin/Android project that builds multiple applications from a shared
**ContentApp** codebase. Product flavors define app-specific content and behavior;
shared modules provide UI, content, audio, monetization, and other capabilities.

[Architecture overview](docs/ARCHITECTURE_STATUS.md) ·
[CI/CD guide](docs/CI_CD.md) ·
[Release health](docs/RELEASE_HEALTH_RUNBOOK.md) ·
[Ask DeepWiki](https://deepwiki.com/MakerParsDev/android-multi-app-framework) (external reference)

## Repository structure

| Path | Purpose |
| --- | --- |
| `app/` | Application entry point and product flavors |
| `core/` | Shared models, design system, persistence, Firebase, and auth |
| `feature/` | Reusable content, audio, ads, billing, and other features |
| `gradle/` and `buildSrc/` | Gradle version catalog and build configuration |
| `scripts/ci/` | Local checks, toolchain verification, and CI utilities |
| `docs/` | Architecture, operations, security, and release documentation |
| `benchmarks/` | Manually run JVM microbenchmarks |

## Local verification

The repository's documented toolchain uses **Java 21**, **Android SDK 37**, and
**Python 3.12** (plus **Node.js 22** for supported side projects). Use the
repository's Gradle wrapper and verification script rather than substituting
unreviewed toolchain versions.

For repository checks that do not require an Android SDK or private Firebase
configuration:

```bash
scripts/ci/local-full-verification.sh --skip-android --skip-firebase
```

For the documented full Android verification workflow, see the
[CI/CD guide](docs/CI_CD.md). This requires the appropriate Android toolchain
and configuration; the release and production signing paths are separate.
**Do not commit secrets, signing files, or per-flavor `google-services.json` files.**

## Quality and contributions

GitHub Actions enforces the pull-request and protected-`main` quality gates,
including Android tests, lint, coverage, security checks, and CodeQL. Mergify
is the sole merge authority; see [AGENTS.md](AGENTS.md) and the
[CI/CD operating model](docs/CI_CD.md) before proposing changes.

## Performance benchmarks

JVM microbenchmarks for Android-independent logic (prayer-time calculations,
location matching, log sanitization, and ad policies) run manually with JMH:

```bash
git submodule update --init --recursive
./gradlew -p benchmarks jmh
```

Hosted CodSpeed comparisons are retired and are not required CI checks.
