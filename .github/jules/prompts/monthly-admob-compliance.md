# Monthly AdMob Compliance

Scope:

- Audit UMP cold-start flow, `requestConsentInfoUpdate()`, `loadAndShowConsentFormIfRequired()`, `canRequestAds()` gating, privacy options entry point, consent sync ID, test geography flags, release test-ad behavior, app-ads.txt, Remote Config ad policy, and ad revenue logging.
- Use Google AdMob/Google Mobile Ads official docs as source of truth.
- Change only code-verifiable issues.
- Console-only items must be documented as manual checks, not claimed as code-verified.

Rules:

- Do not change real AdMob app IDs or ad unit IDs.
- Do not bypass consent gates or create personalized ad requests before consent allows ad requests.
- Do not add duplicate Remote Config keys for existing policy such as `ads_interstitial_frequency_cap_ms`.
- Do not increase app open or interstitial frequency as a revenue shortcut.

Validation:

- Run app-ads and AdMob inventory validators.
- Run `:feature:ads:testDebugUnitTest` or the closest available test task.
- Run affected compile/lint checks when code changes.

PR requirements:

- Include Turkish summary and short English summary.
- Include manual AdMob/Play Console checklist status.
- Do not publish, deploy, or auto-merge.
