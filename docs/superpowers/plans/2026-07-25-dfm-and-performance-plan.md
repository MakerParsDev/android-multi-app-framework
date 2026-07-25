# DFM & Performance Optimization Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement Dynamic Feature Delivery (`:feature:dynamic_audio`) and `androidx.startup` cold start optimization across all 17 product flavors.

**Architecture:** Create `:feature:dynamic_audio` using `com.android.dynamic-feature` plugin. Integrate `androidx.startup` initializers in `:core:common` to streamline SDK initialization on cold start.

**Tech Stack:** Kotlin 2.x, `com.android.dynamic-feature`, `com.google.android.play:feature-delivery`, `androidx.startup:startup-runtime`, Baseline Profiles.

## Global Constraints

- Android `minSdk`: 26, `compileSdk`: 35, `targetSdk`: 35
- Java Toolchain: 21
- Package namespace base: `com.parsfilo.contentapp`

---

### Task 1: App Startup Initializer Chain Integration (:core:common)

**Files:**
- Create: `core/common/src/main/java/com/parsfilo/contentapp/core/common/startup/AppInitializer.kt`
- Create: `core/common/src/test/java/com/parsfilo/contentapp/core/common/startup/AppInitializerTest.kt`

- [ ] **Step 1: Write failing unit test for AppInitializer**

Create `AppInitializerTest.kt` verifying initializer dependency ordering.

- [ ] **Step 2: Run test to verify it fails**

Run: `.\gradlew :core:common:test`  
Expected: FAIL with missing `AppInitializer`

- [ ] **Step 3: Implement AppInitializer**

Implement `AppInitializer` implementing `androidx.startup.Initializer<Unit>`.

- [ ] **Step 4: Run test to verify it passes**

Run: `.\gradlew :core:common:test`  
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add core/common/
git commit -m "feat(startup): add androidx.startup AppInitializer for cold start optimization"
```

---

### Task 2: Dynamic Feature Module Setup (:feature:dynamic_audio)

**Files:**
- Create: `feature/dynamic_audio/build.gradle.kts`
- Modify: `settings.gradle.kts:70-75`

- [ ] **Step 1: Create feature/dynamic_audio/build.gradle.kts**

Declare `com.android.dynamic-feature` plugin and Play Feature Delivery dependencies.

- [ ] **Step 2: Add include to settings.gradle.kts**

Add `include(":feature:dynamic_audio")` to `settings.gradle.kts`.

- [ ] **Step 3: Verify Gradle sync**

Run: `.\gradlew help`  
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add settings.gradle.kts feature/dynamic_audio/build.gradle.kts
git commit -m "build: scaffold :feature:dynamic_audio dynamic feature module"
```
