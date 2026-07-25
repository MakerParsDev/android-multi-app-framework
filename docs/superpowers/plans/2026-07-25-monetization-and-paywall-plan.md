# Advanced Monetization & Paywall UX Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement Compose Native Ad Cards (`:feature:ads`) and A/B Tested Paywall screens (`:feature:billing`).

**Architecture:** Custom `ComposeNativeAdCard` wrapping Android `NativeAdView` for smooth feed scrolling. Remote Config variant selector powering `DynamicPaywallScreen`.

**Tech Stack:** Jetpack Compose, AdMob/AdX Native Ads (`play-services-ads`), Play Billing KTX (`billing-ktx`), Firebase Remote Config.

## Global Constraints

- Android `minSdk`: 26, `compileSdk`: 35, `targetSdk`: 35
- Java Toolchain: 21

---

### Task 1: Compose Native Ad Feed Card (:feature:ads)

**Files:**
- Create: `feature/ads/src/main/java/com/parsfilo/contentapp/feature/ads/ui/ComposeNativeAdCard.kt`
- Create: `feature/ads/src/test/java/com/parsfilo/contentapp/feature/ads/ui/ComposeNativeAdCardTest.kt`

- [ ] **Step 1: Write failing unit test for ComposeNativeAdCard state**

Create `ComposeNativeAdCardTest.kt` checking ad placement state mapping.

- [ ] **Step 2: Run test to verify it fails**

Run: `.\gradlew :feature:ads:test`  
Expected: FAIL with missing `ComposeNativeAdCard`

- [ ] **Step 3: Implement ComposeNativeAdCard**

Implement `ComposeNativeAdCard` using `AndroidView` wrapping `NativeAdView`.

- [ ] **Step 4: Run test to verify it passes**

Run: `.\gradlew :feature:ads:test`  
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add feature/ads/
git commit -m "feat(ads): add ComposeNativeAdCard for inline feed ad placement"
```

---

### Task 2: Dynamic A/B Tested Paywall Screen (:feature:billing)

**Files:**
- Create: `feature/billing/src/main/java/com/parsfilo/contentapp/feature/billing/ui/DynamicPaywallScreen.kt`
- Create: `feature/billing/src/test/java/com/parsfilo/contentapp/feature/billing/ui/DynamicPaywallScreenTest.kt`

- [ ] **Step 1: Write failing unit test for paywall variant selection**

Create `DynamicPaywallScreenTest.kt` testing variant config parsing.

- [ ] **Step 2: Run test to verify it fails**

Run: `.\gradlew :feature:billing:test`  
Expected: FAIL with missing `DynamicPaywallScreen`

- [ ] **Step 3: Implement DynamicPaywallScreen**

Implement `DynamicPaywallScreen` displaying hero header, features, pricing cards, and trial badges.

- [ ] **Step 4: Run test to verify it passes**

Run: `.\gradlew :feature:billing:test`  
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add feature/billing/
git commit -m "feat(billing): add DynamicPaywallScreen for Remote Config A/B testing"
```
