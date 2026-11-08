# Android UI/UX engineering review — 2026-11-08

Scope: MakerParsDev/android-multi-app-framework, all 17 flavors sharing the
Compose design system. This review focuses on reader space, responsive
advertising, primary navigation, accessibility, and safe library adoption.
It is not a Play certification or a claim that every screen is premium-ready.

## Verified stable technology baseline

- Kotlin 2.4.20, Android Gradle Plugin 9.5.0, Compose BOM 2026.09.00,
  Activity Compose 1.13.0 and Google Mobile Ads SDK 25.5.0 are pinned in
  gradle/libs.versions.toml at the review baseline.
- Official Compose setup documentation recommends BOM 2026.09.00; stable
  Material 3 1.4.0 provides menus and responsive navigation components.
- Android Gradle Plugin 9.5.1 is a later stable patch candidate. Treat it as a
  separate dependency canary/CI decision, not part of a risky UI refactor.
- Google Mobile Ads documentation distinguishes **inline adaptive** ad units
  for scrolling content from **anchored adaptive** banners and requires an
  ad request width matching the actual available container.
- Android adaptive layout guidance requires testing window size changes,
  landscape, text scaling, multi-window and system-navigation insets.

Official references (checked 2026-11-08):
- https://developer.android.com/develop/ui/compose/setup-compose-dependencies-and-compiler
- https://developer.android.com/jetpack/androidx/releases/compose-material3
- https://developer.android.com/develop/ui/compose/components/menu
- https://developer.android.com/develop/adaptive-apps/guides/use-window-size-classes
- https://developer.android.com/develop/ui/compose/system/material-insets
- https://developers.google.com/admob/android/banner
- https://developers.google.com/admob/android/banner/inline-adaptive
- https://developer.android.com/reference/tools/gradle-api
- https://developers.google.com/admob/android/rel-notes

## Findings and narrow fixes

| Concern | Baseline finding | Target implementation |
| --- | --- | --- |
| Reading viewport | Long content permanently reserves horizontal rows for jumping, saving, resuming and clearing reading position. | Move optional actions behind the accessible reader-tools overflow menu; preserve jump, bookmark, resume and localized labels. |
| Ads | Inline adaptive banner width followed full parent width, including wide/freeform windows. Fixed standard requests could exceed a narrower slot. | Use bounded **measured parent** widths: max 360dp inline and 320dp standard, center the SDK view and clip overflow. Keep inline adaptive only for scrolling content; omit the 320×50 fixed banner where the measured slot is below 320dp rather than replacing it with a taller large-anchored adaptive creative. Skip zero-width slots without sending a request. Preserve consent, premium gates and ad metrics. |
| Reading modes | At 1.8x system font size on a 411dp Samsung, Arabic/Latin/meal modes become three vertically stacked rows, consuming the verse viewport. | Keep a single horizontal 48dp+ touch target row, scrolling on narrower viewports/large fonts; update both shared reader and prayer detail. |
| Navigation | The compact bar's available-width calculation did not include the horizontal navigation insets applied by the bar itself. | Apply navigation-bar insets once to the measured parent, disable Material 3's duplicate insets, keep a 68dp application bar plus system safe area, and calculate label visibility from the resulting usable width. |
| Screen-size strategy | Material 3 adaptive navigation suite already exists for medium/expanded windows. | Retain navigation suite; fix compact boundary only. Avoid a second bespoke navigation stack. |
| Library strategy | Compose BOM, Material 3, Activity and Ads SDK are on compatible stable tracks. | Preserve pinned compatibility, test AGP patch upgrades separately. Do not adopt previews or replace SDKs without an acceptance benefit. |

## Acceptance matrix and stop rules

Every changed reusable component must pass:
1. Local compilation, ktlint, affected module unit tests and SDK/API compatibility checks.
2. A representative real Android 9 device smoke across a long reader (Yasin) and short/prayer reader where applicable; no overwriting Play-signed packages.
3. Reader: jump to verse 12, save and resume, translation controls, large fonts, light/dark theme; controls must remain discoverable without displacing verses. At 1.8x text size, the three reading modes must stay in one horizontally scrollable row (not three tall rows).
4. Navigation: effective compact heights and five click/touch targets at 320/360/411dp, larger font, landscape and gesture/three-button navigation; medium/expanded windows keep navigation-suite behavior.
5. Ads: test ad labels and request-slot width bounds across 280/320/360/411/840dp, verify inline-vs-fixed selection, narrow fixed-slot suppression, and zero-width suppression, SDK view sizing, placement after content, no click during acceptance and no change to consent/premium gate.
6. Physical QA: inspect the exact package ID on the device before installation; never replace or uninstall an existing Play-installed package. If the target flavor is already installed, use an isolated QA package instead.
7. All 17 flavors must pass compilation and at least representative UI smoke. The earlier 17/17 physical baseline was **sampled UI only**, not full end-to-end release acceptance.
8. Exact-PR-head GitHub required checks, CodeQL, SonarCloud and security succeed; merge only through Mergify. No automatic Play publish.

The following remain **separate, not yet certified by this review**: real production
ad creatives and height transitions, premium Play Billing entitlement in a licensed
account, actual acoustic playback for all audio titles, fully spoken TalkBack focus
order, a complete matrix of foldable/tablet/high-DPI devices, and Play foreground
service declarations. Do not label them PASS based solely on unit tests or smoke
APK behavior. Stop and investigate a reproducible regression rather than
weakening assertion/coverage/security gates.

## Local and physical evidence (2026-11-08)

- MSI Ubuntu, Java 21/SDK 37.0/Gradle 9.8.2: Yasin debug APK and instrumentation APK assembled; ads and content module JUnit and application/ads/content ktlint passed. Build log: `/tmp/android-ui-acceptance-build.log` (`BUILD SUCCESSFUL`, 621 actionable tasks).
- AMD2700x USB physical Samsung SM-G611F, Android 9, 1080×1920, 420 dpi: tested the **exact local QA APK** with `-PciSmoke=true`; did not replace the existing `com.parsfilo.yasinsuresi.uiqa`. Installed separate `com.parsfilo.yasinsuresi` and `com.parsfilo.yasinsuresi.test` test-only packages after verifying absent.
- Actual `adb shell am instrument` suite: **12/12 PASS** in 28.155 seconds, covering Yasin verse 12 jump/save/resume (1), reader 200% font and selector (3), Turkish/English/German locale and large-font label display (6), and five navigation targets/labels at 320/411dp (2). Private device transcript: `%TEMP%\\android-reader-uiqa-20261108\\reader-acceptance-12-tests.txt`.
- The initial horizontal-scroller assertions incorrectly treated clipped off-screen text bounds as displayed coordinates; assertions now `performScrollTo()` first. They passed without weakening the one-row, non-truncation, or accessibility assertions.
- **Not yet passed by this test**: production real SDK creatives, all 17 flavor compilation and real-device matrix, exact-PR checks, landscape three-button navigation with system insets, signed Play acoustic/Billing, and spoken TalkBack order. Keep these as separate acceptance gates.
