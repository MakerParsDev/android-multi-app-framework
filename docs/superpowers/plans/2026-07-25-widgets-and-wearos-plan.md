# Jetpack Glance Widgets & WearOS Integration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement Jetpack Glance home/lock screen widgets (`:feature:widget`) and WearOS smartwatch sync (`:feature:wear`) across all 17 product flavors.

**Architecture:** Create two new standalone feature modules (`:feature:widget` and `:feature:wear`). Integrate GlanceAppWidget with Room/DataStore state and WorkManager background refresh. Implement Wear DataClient listener to sync Zikir & Prayer state bi-directionally.

**Tech Stack:** Kotlin 2.x, Jetpack Compose, Jetpack Glance (`androidx.glance:glance-appwidget`), Wear Compose (`androidx.wear.compose:compose-material`), Google Play Services Wearable (`com.google.android.gms:play-services-wearable`), Hilt, Coroutines Flow.

## Global Constraints

- Android `minSdk`: 26, `compileSdk`: 35, `targetSdk`: 35
- Java Toolchain: 21
- Package namespace base: `com.parsfilo.contentapp`
- Strict Clean Architecture: Feature modules depend only on `:core:*` modules, never on other `:feature:*` modules.

---

### Task 1: Module Setup & Gradle Configuration

**Files:**
- Modify: `settings.gradle.kts:15-30`
- Create: `feature/widget/build.gradle.kts`
- Create: `feature/wear/build.gradle.kts`
- Modify: `app/build.gradle.kts:565-580`

**Interfaces:**
- Consumes: N/A
- Produces: Gradle module paths `:feature:widget` and `:feature:wear` available to `:app`.

- [ ] **Step 1: Write module includes in settings.gradle.kts**

Add `:feature:widget` and `:feature:wear` to `settings.gradle.kts`.

- [ ] **Step 2: Create feature/widget/build.gradle.kts**

Create `feature/widget/build.gradle.kts` declaring `com.android.library` plugin and Glance dependencies (`libs.androidx.glance.appwidget`).

- [ ] **Step 3: Create feature/wear/build.gradle.kts**

Create `feature/wear/build.gradle.kts` declaring `com.android.library` plugin and Wear Compose dependencies (`libs.androidx.wear.compose`).

- [ ] **Step 4: Verify Gradle sync**

Run: `.\gradlew help`  
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add settings.gradle.kts feature/widget/build.gradle.kts feature/wear/build.gradle.kts app/build.gradle.kts
git commit -m "build: scaffold :feature:widget and :feature:wear modules"
```

---

### Task 2: Implement Glance Widget UI & Receiver (:feature:widget)

**Files:**
- Create: `feature/widget/src/main/java/com/parsfilo/contentapp/feature/widget/ZikirGlanceWidget.kt`
- Create: `feature/widget/src/main/java/com/parsfilo/contentapp/feature/widget/ZikirGlanceWidgetReceiver.kt`
- Create: `feature/widget/src/test/java/com/parsfilo/contentapp/feature/widget/ZikirGlanceWidgetStateTest.kt`

**Interfaces:**
- Consumes: `:core:datastore` counter preference Flow
- Produces: `ZikirGlanceWidget` component and receiver for Home screen widgets

- [ ] **Step 1: Write failing unit test for Glance widget state mapping**

Create `ZikirGlanceWidgetStateTest.kt` verifying state transformation from data model to Glance widget parameters.

- [ ] **Step 2: Run test to verify it fails**

Run: `.\gradlew :feature:widget:test`  
Expected: FAIL with missing `ZikirGlanceWidget`

- [ ] **Step 3: Implement ZikirGlanceWidget & ZikirGlanceWidgetReceiver**

Implement `ZikirGlanceWidget` extending `GlanceAppWidget` with Compose Glance layout and `ZikirGlanceWidgetReceiver` extending `GlanceAppWidgetReceiver`.

- [ ] **Step 4: Run test to verify it passes**

Run: `.\gradlew :feature:widget:test`  
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add feature/widget/
git commit -m "feat(widget): add ZikirGlanceWidget and GlanceAppWidgetReceiver"
```

---

### Task 3: Implement WearOS Data Client Sync (:feature:wear)

**Files:**
- Create: `feature/wear/src/main/java/com/parsfilo/contentapp/feature/wear/WearDataSyncListener.kt`
- Create: `feature/wear/src/test/java/com/parsfilo/contentapp/feature/wear/WearDataSyncListenerTest.kt`

**Interfaces:**
- Consumes: Wearable Data Layer API (`DataClient`)
- Produces: `WearDataSyncListener` service for background phone-watch state sync.

- [ ] **Step 1: Write failing unit test for Wear DataMap serialization**

Create `WearDataSyncListenerTest.kt` verifying serializing Zikir state to `DataMap`.

- [ ] **Step 2: Run test to verify it fails**

Run: `.\gradlew :feature:wear:test`  
Expected: FAIL with missing `WearDataSyncListener`

- [ ] **Step 3: Implement WearDataSyncListener**

Implement `WearDataSyncListener` extending `WearableListenerService` to receive and process watch events.

- [ ] **Step 4: Run test to verify it passes**

Run: `.\gradlew :feature:wear:test`  
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add feature/wear/
git commit -m "feat(wear): add WearDataSyncListener for smartwatch state synchronization"
```

---

### Task 4: Integration Verification & Quality Gate Check

**Files:**
- Modify: `app/src/main/AndroidManifest.xml` (register Glance receivers and Wearable service)

- [ ] **Step 1: Run repository quality gate check**

Run: `.\gradlew qualityCheck`  
Expected: BUILD SUCCESSFUL

- [ ] **Step 2: Commit**

```bash
git add docs/superpowers/plans/2026-07-25-widgets-and-wearos-plan.md
git commit -m "docs: add implementation plan for Glance Widgets and WearOS integration"
```
