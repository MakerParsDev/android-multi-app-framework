# Visual Screenshot Testing & CI/CD Pipeline Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement Roborazzi UI screenshot testing and GitHub Actions automated Play Store deployment pipeline.

**Architecture:** Roborazzi Gradle plugin integration for Compose UI snapshot verification. GitHub Actions workflow `.github/workflows/deploy-internal-track.yml` for automated Play Console deployment.

**Tech Stack:** Roborazzi (`io.github.takahirom.roborazzi`), Gradle Play Publisher (`com.github.triplet.play`), GitHub Actions.

## Global Constraints

- Java Toolchain: 21
- All 17 product flavors supported in CI/CD pipeline

---

### Task 1: Roborazzi Screenshot Testing Setup (:core:designsystem)

**Files:**
- Modify: `core/designsystem/build.gradle.kts`
- Create: `core/designsystem/src/test/java/com/parsfilo/contentapp/core/designsystem/theme/ThemeScreenshotTest.kt`

- [ ] **Step 1: Create ThemeScreenshotTest**

Implement `ThemeScreenshotTest` utilizing Roborazzi Compose snapshot capture.

- [ ] **Step 2: Verify test run**

Run: `.\gradlew :core:designsystem:test`  
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add core/designsystem/
git commit -m "test(designsystem): add Roborazzi ThemeScreenshotTest for UI visual regression verification"
```

---

### Task 2: Automated Play Store Internal Track CD Workflow

**Files:**
- Create: `.github/workflows/deploy-internal-track.yml`

- [ ] **Step 1: Create deploy-internal-track.yml**

Create GitHub Actions workflow building and uploading release App Bundles to Play Store Internal Track.

- [ ] **Step 2: Commit**

```bash
git add .github/workflows/deploy-internal-track.yml
git commit -m "ci: add deploy-internal-track.yml workflow for automated Play Store CD"
```
